import { errorResponse } from "./health";
import type { SessionRegistry } from "./sessions";
import { extractBearer, validateRouteClaim } from "./tokens";
import { hashId, type Metrics } from "./observability";
import type { RouteScope } from "./config";

type RouteEndpoint = { method: string; pattern: RegExp; requiredScope: RouteScope };

const ROUTE_ENDPOINTS: readonly RouteEndpoint[] = [
  { method: "GET", pattern: /^\/healthz$/, requiredScope: "events" },
  { method: "GET", pattern: /^\/v1\/help$/, requiredScope: "events" },
  { method: "GET", pattern: /^\/v1\/handshake$/, requiredScope: "events" },
  { method: "GET", pattern: /^\/v1\/sessions\/[^/]+\/events$/, requiredScope: "events" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/commands$/, requiredScope: "prompt" },
  { method: "GET", pattern: /^\/v1\/sessions\/[^/]+\/ui-responses$/, requiredScope: "ui_responses" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/ui-responses$/, requiredScope: "ui_responses" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/claim-control$/, requiredScope: "claim_control" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/disconnect-control$/, requiredScope: "claim_control" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/host-tool-results$/, requiredScope: "permission" },
  { method: "POST", pattern: /^\/v1\/sessions\/[^/]+\/host-uri-results$/, requiredScope: "permission" },
];

function requiredScopeFor(method: string, pathname: string): RouteScope | undefined {
  return ROUTE_ENDPOINTS.find(endpoint => endpoint.method === method && endpoint.pattern.test(pathname))?.requiredScope;
}

function routeDenied(routeId: string, status = 403): Response {
  return errorResponse("scope_denied", "Route endpoint is not allowed for this token scope.", false, { routeIdHash: hashId(routeId, "hash_route"), bridgeContacted: false }, status);
}

export async function proxyRoute(req: Request, routeId: string, rest: string, registry: SessionRegistry, metrics: Metrics): Promise<Response> {
  const pathname = rest.startsWith("/") ? rest : `/${rest}`;
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
    return errorResponse(validation.code, validation.code === "scope_denied" ? "Route token does not allow this scope." : "Missing or invalid route token.", false, validation.details, validation.code === "scope_denied" ? 403 : 401);
  }

  const session = registry.route(routeId);
  if (!session || session.id !== validation.claim.sessionId || session.projectId !== validation.claim.projectId) {
    return errorResponse("route_token_mismatch", "Route token is not bound to an active session route.", false, { routeIdHash: hashId(routeId, "hash_route"), bridgeContacted: false }, 403);
  }

  const upstream = new URL(session.bridgeUrl);
  upstream.pathname = pathname;
  upstream.search = new URL(req.url).search;
  const headers = new Headers(req.headers);
  headers.set("authorization", `Bearer ${session.bridgeToken}`);
  headers.delete("host");
  try {
    const init: RequestInit = req.method === "GET" || req.method === "HEAD" ? { method: req.method, headers } : { method: req.method, headers, body: req.body };
    const res = await fetch(upstream, init);
    const outHeaders = new Headers(res.headers);
    outHeaders.delete("content-length");
    outHeaders.delete("transfer-encoding");
    return new Response(res.body, { status: res.status, headers: outHeaders });
  } catch {
    return errorResponse("bridge_unhealthy", "Route proxy could not establish a healthy bridge stream.", true, { routeIdHash: hashId(routeId, "hash_route"), upstream: "bridge_alias_primary" }, 502);
  }
}
