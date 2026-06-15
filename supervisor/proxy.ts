import { readdir, readFile, stat } from "node:fs/promises";
import { join } from "node:path";
import { homedir } from "node:os";
import { errorResponse } from "./health";
import type { SessionRegistry } from "./sessions";
import { extractBearer, validateRouteClaim } from "./tokens";
import { hashId, type Metrics } from "./observability";
import type { RouteScope } from "./config";

type RouteEndpoint = { method: string; pattern: RegExp; requiredScope: RouteScope };
type HistoryMessage = { role: "user" | "assistant"; text: string; ts?: string };

const ROUTE_ENDPOINTS: readonly RouteEndpoint[] = [
  // /healthz and /v1/help are public on the bridge (protocol v2: auth "no"); handled without a route token below.
  { method: "GET", pattern: /^\/v1\/handshake$/, requiredScope: "events" },
  { method: "GET", pattern: /^\/v1\/sessions\/[^/]+\/events$/, requiredScope: "events" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/commands$/, requiredScope: "prompt" },
  { method: "GET", pattern: /^\/v1\/sessions\/[^/]+\/ui-responses$/, requiredScope: "ui_responses" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/ui-responses$/, requiredScope: "ui_responses" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/claim-control$/, requiredScope: "claim_control" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/disconnect-control$/, requiredScope: "claim_control" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/host-tool-results$/, requiredScope: "permission" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/host-uri-results$/, requiredScope: "permission" },
  // Real gjc bridge protocol forms (POST handshake; colon control verbs; correlation-id suffixes).
  { method: "POST", pattern: /^\/v1\/handshake$/, requiredScope: "events" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/control:claim$/, requiredScope: "claim_control" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/control:disconnect$/, requiredScope: "claim_control" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/ui-responses\/[^/]+$/, requiredScope: "ui_responses" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/host-tool-results\/[^/]+$/, requiredScope: "permission" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/host-uri-results\/[^/]+$/, requiredScope: "permission" },
];

function requiredScopeFor(method: string, pathname: string): RouteScope | undefined {
  return ROUTE_ENDPOINTS.find(endpoint => endpoint.method === method && endpoint.pattern.test(pathname))?.requiredScope;
}

const PUBLIC_ROUTE_ENDPOINTS: readonly { method: string; pattern: RegExp }[] = [
  { method: "GET", pattern: /^\/healthz$/ },
  { method: "GET", pattern: /^\/v1\/help$/ },
];
function isPublicRouteEndpoint(method: string, pathname: string): boolean {
  return PUBLIC_ROUTE_ENDPOINTS.some(e => e.method === method && e.pattern.test(pathname));
}

function routeDenied(routeId: string, status = 403): Response {
  return errorResponse("scope_denied", "Route endpoint is not allowed for token scope.", false, { routeIdHash: hashId(routeId, "hash_route"), bridgeContacted: false }, status);
}

export function isInitialEventsRequest(req: Request, pathname: string): boolean {
  if (req.method !== "GET" || !/^\/v1\/sessions\/[^/]+\/events$/.test(pathname)) return false;
  const url = new URL(req.url);
  const raw = url.searchParams.get("last_seq");
  if (raw === null) return true;
  if (raw.trim() === "") return false;
  const n = Number(raw);
  return Number.isFinite(n) && n === 0;
}

export function projectSlug(cwd: string): string {
  const home = homedir();
  const stripped = cwd === home ? "" : cwd.startsWith(`${home}/`) ? cwd.slice(home.length) : cwd;
  return stripped.replace(/\//g, "-") || "-";
}

function sessionsRoot(): string {
  return process.env.GJC_AGENT_SESSIONS_DIR ?? join(homedir(), ".gjc", "agent", "sessions");
}

export async function locateLatestVerifiedGjcJsonl(cwd: string, metrics?: Metrics): Promise<string | undefined> {
  const dir = join(sessionsRoot(), projectSlug(cwd));
  let names: string[];
  try {
    names = (await readdir(dir)).filter(name => name.endsWith(".jsonl"));
  } catch {
    metrics?.inc("history.inject.skip", "missing_dir");
    return undefined;
  }
  const verified: { path: string; mtimeMs: number }[] = [];
  let rejected = 0;
  for (const name of names) {
    const path = join(dir, name);
    try {
      const first = (await readFile(path, "utf8")).split(/\r?\n/, 1)[0];
      const meta = JSON.parse(first ?? "");
      if (meta?.type === "session" && meta?.cwd === cwd) verified.push({ path, mtimeMs: (await stat(path)).mtimeMs });
      else rejected++;
    } catch {
      rejected++;
    }
  }
  if (!verified.length) {
    metrics?.inc("history.inject.skip", rejected ? "cwd_unverified" : "empty_dir");
    return undefined;
  }
  verified.sort((a, b) => b.mtimeMs - a.mtimeMs);
  return verified[0]!.path;
}

function extractText(content: unknown): string {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return "";
  return content.map(part => {
    if (!part || typeof part !== "object") return "";
    const p = part as Record<string, unknown>;
    if (p.type && p.type !== "text") return "";
    return typeof p.text === "string" ? p.text : "";
  }).filter(Boolean).join("\n");
}

export async function readHistoryMessages(cwd: string, limit = 100, metrics?: Metrics): Promise<HistoryMessage[]> {
  const file = await locateLatestVerifiedGjcJsonl(cwd, metrics);
  if (!file) return [];
  try {
    const rows: HistoryMessage[] = [];
    for (const line of (await readFile(file, "utf8")).split(/\r?\n/)) {
      if (!line.trim()) continue;
      try {
        const obj = JSON.parse(line);
        const msg = obj?.type === "message" ? obj.message : undefined;
        const role = msg?.role;
        if (role !== "user" && role !== "assistant") continue;
        const text = extractText(msg.content).trim();
        if (!text) continue;
        rows.push({ role, text, ts: obj.timestamp ?? obj.ts ?? msg.created_at ?? msg.ts });
      } catch {
        metrics?.inc("history.inject.skip", "line_parse");
      }
    }
    return rows.slice(-limit);
  } catch {
    metrics?.inc("history.inject.skip", "read_failed");
    return [];
  }
}

export function historyFrames(session: { id: string; gjcSessionId: string }, messages: HistoryMessage[]): unknown[] {
  return messages.map((msg, index) => ({
    type: "history",
    frameId: `history:${session.id}:${index}`,
    protocolVersion: 2,
    sessionId: session.gjcSessionId,
    raw: { type: "history", isHistory: true, role: msg.role, text: msg.text, ts: msg.ts ?? null, history_seq: index, source: "gjc-jsonl" },
  }));
}

export function encodeSse(frame: unknown): Uint8Array {
  return new TextEncoder().encode(`data: ${JSON.stringify(frame)}\n\n`);
}

export function prependHistoryStream(frames: unknown[], upstreamBody: ReadableStream<Uint8Array> | null): ReadableStream<Uint8Array> {
  const reader = upstreamBody?.getReader();
  return new ReadableStream<Uint8Array>({
    async start(controller) {
      for (const frame of frames) controller.enqueue(encodeSse(frame));
      if (!reader) { controller.close(); return; }
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          if (value) controller.enqueue(value);
        }
        controller.close();
      } catch (error) {
        controller.error(error);
      }
    },
    cancel(reason) {
      return reader?.cancel(reason);
    },
  });
}

function strippedHeaders(headers: Headers): Headers {
  const out = new Headers(headers);
  out.delete("content-length");
  out.delete("transfer-encoding");
  out.delete("content-encoding");
  out.delete("etag");
  return out;
}

export async function proxyRoute(req: Request, routeId: string, rest: string, registry: SessionRegistry, metrics: Metrics): Promise<Response> {
  const pathname = rest.startsWith("/") ? rest : `/${rest}`;
  let session;
  if (isPublicRouteEndpoint(req.method, pathname)) {
    session = registry.route(routeId);
    if (!session) return errorResponse("route_token_mismatch", "Route is not bound to an active session route.", false, { routeIdHash: hashId(routeId, "hash_route"), bridgeContacted: false }, 404);
  } else {
    const requiredScope = requiredScopeFor(req.method, pathname);
    if (!requiredScope) {
      metrics.inc("route.reject", "scope_denied");
      return routeDenied(routeId);
    }
    const token = extractBearer(req.headers.get("authorization"));
    const validation = validateRouteClaim(token, routeId, requiredScope);
    if (!validation.ok) {
      metrics.inc("route.reject", validation.code);
      if (validation.code === "route_token_mismatch") return errorResponse("route_token_mismatch", "Route token is not bound to the requested route.", false, validation.details, 403);
      return errorResponse(validation.code, validation.code === "scope_denied" ? "Route token does not allow this scope." : "Missing or invalid route token.", false, {}, validation.code === "unauthorized" ? 401 : 403);
    }
    session = registry.route(routeId);
    if (!session || session.id !== validation.claim.sessionId || session.projectId !== validation.claim.projectId) {
      return errorResponse("route_token_mismatch", "Route token is not bound to an active session route.", false, { routeIdHash: hashId(routeId, "hash_route"), bridgeContacted: false }, 403);
    }
  }

  const upstream = new URL(session.bridgeUrl);
  upstream.pathname = pathname;
  upstream.search = new URL(req.url).search;
  const headers = new Headers(req.headers);
  headers.set("authorization", `Bearer ${session.bridgeToken}`);
  headers.set("accept-encoding", "identity");
  headers.delete("host");
  try {
    const init: RequestInit = req.method === "GET" || req.method === "HEAD" ? { method: req.method, headers } : { method: req.method, headers, body: req.body };
    const res = await fetch(upstream, init);
    const outHeaders = strippedHeaders(res.headers);
    if (isInitialEventsRequest(req, pathname)) {
      const messages = await readHistoryMessages(registry.projectCwd(session.projectId) ?? "", 100, metrics);
      const frames = historyFrames(session, messages);
      if (frames.length) return new Response(prependHistoryStream(frames, res.body), { status: res.status, headers: outHeaders });
    }
    return new Response(res.body, { status: res.status, headers: outHeaders });
  } catch {
    return errorResponse("bridge_unhealthy", "Route proxy could not establish a healthy bridge stream.", true, { routeIdHash: hashId(routeId, "hash_route"), upstream: "bridge_alias_primary" }, 502);
  }
}
