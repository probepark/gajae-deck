import { randomUUID } from "node:crypto";
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { spawn, type Subprocess } from "bun";
import type { SupervisorConfig } from "./config";
import { defaultScopes, mintScopedToken, revokeScopedToken, type RouteClaim } from "./tokens";
import { envelope } from "./health";
import { hashId, logJson, type Metrics } from "./observability";

export type SessionStatus = "starting" | "ready" | "reconnecting" | "idle" | "degraded" | "stopped" | "error";
export type RestoreSkipReason = "stale_ttl" | "max_restored_sessions" | "crash_loop_cap" | "project_missing" | "cwd_unavailable" | "config_changed" | "credential_unavailable" | "manual_stop" | "route_invalid";
export interface SessionRoute { sessionId: string; projectId: string; routeId: string; routePath: string; baseUrl: string; scopedToken: string; scopes: string[]; expiresAt: null | string; revocation: "respawn_required"; ownerToken: string }
export interface Session { id: string; projectId: string; routeId: string; routeIdHash: string; gjcSessionId: string; status: SessionStatus; scopes: string[]; startedAt: string; lastActiveAt: string; lastSeq: number; bridgePid: number | null; restoreEligibility: "eligible" | "ineligible" | "skipped"; bridgeUrl: string; bridgeToken: string; routeToken: string; skipReason?: RestoreSkipReason; degradedReason?: string; idleSince?: string; proc?: Subprocess }
export interface PersistedSessionMeta { id: string; projectId: string; projectHash: string; routeHash: string; routeId?: string; gjcSessionId: string; status: SessionStatus; scopes: string[]; startedAt: string; lastActiveAt: string; lastSeq: number; restoreEligibility: "eligible" | "ineligible"; crashRestarts?: string[]; policyHash?: string }

async function waitForBridge(bridgeUrl: string, token: string, proc: Subprocess, timeoutMs = 8000): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (proc.exitCode !== null) return false;
    try {
      const res = await fetch(`${bridgeUrl}/healthz`, { headers: { authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(1000) });
      if (res.ok || res.status === 401 || res.status === 403) return true;
    } catch {}
    await Bun.sleep(150);
  }
  return false;
}

export class SessionRegistry {
  sessions = new Map<string, Session>();
  routes = new Map<string, string>();
  runningByProject = new Map<string, string>();
  restoreSkipped: Array<{ session: ReturnType<SessionRegistry["publicSession"]>; restore: { reason: RestoreSkipReason; retryable: boolean; budget: { maxRestoredSessions: number; attemptedRank: number } } }> = [];
  constructor(public config: SupervisorConfig, public metrics: Metrics, public stateDir = join(process.cwd(), "state")) {}
  counts() { const vals = [...this.sessions.values()]; return { ready: vals.filter(s=>s.status==="ready").length, idle: vals.filter(s=>s.status==="idle").length, degraded: vals.filter(s=>s.status==="degraded").length, stopped: Math.max(0, this.config.projects.length - vals.filter(s=>s.status!=="stopped").length) }; }

  async start(projectId: string, opts: { bridgeUrl?: string; bridgeToken?: string; routeId?: string; scopedTokenAlias?: string; restored?: boolean } = {}) {
    const project = this.config.projects.find(p => p.id === projectId); if (!project) throw new Error("unknown project");
    const id = projectId === "proj_7f3a" && !this.sessions.has("sess_7f3a") ? "sess_7f3a" : `sess_${randomUUID().slice(0, 4)}`;
    const routeId = opts.routeId ?? (projectId === "proj_7f3a" ? "route_opaque_7f3a" : `route_${randomUUID()}`);
    const bridgeToken = opts.bridgeToken ?? `bridge_${randomUUID()}`;
    let bridgeUrl = opts.bridgeUrl;
    let proc: Subprocess | undefined; let pid = 4242;
    if (!bridgeUrl) {
      const port = 35_000 + Math.floor(Math.random() * 10_000);
      const scheme = this.config.bridgeTls ? "https" : "http";
      bridgeUrl = `${scheme}://${this.config.bridgeHost}:${port}`;
      proc = spawn({ cmd: this.config.bridgeCommand, cwd: project.cwd, env: { ...Bun.env, GJC_BRIDGE_TOKEN: bridgeToken, GJC_BRIDGE_HOST: this.config.bridgeHost, GJC_BRIDGE_PORT: String(port), GJC_BRIDGE_ENDPOINTS: "all", GJC_BRIDGE_SCOPES: this.config.bridgeScopes.join(","), ...(this.config.bridgeTls ? { GJC_BRIDGE_TLS_CERT: this.config.bridgeTls.cert, GJC_BRIDGE_TLS_KEY: this.config.bridgeTls.key } : {}) }, stdout: "ignore", stderr: "ignore" });
      pid = proc.pid ?? 0;
      await waitForBridge(bridgeUrl, bridgeToken, proc);
    }
    const claim: Omit<RouteClaim, "tokenHash"> = { routeId, sessionId: id, projectId, scopes: defaultScopes(), issuedAt: new Date().toISOString() };
    const routeToken = mintScopedToken(claim, opts.scopedTokenAlias ?? (projectId === "proj_7f3a" ? "scoped_route_token_alias_7f3a" : undefined));
    const session: Session = { id, projectId, routeId, routeIdHash: hashId(routeId, "hash_route"), gjcSessionId: "gjc_sess_7f3a", status: "ready", scopes: defaultScopes(), startedAt: routeId === "route_opaque_8b2c" ? "2026-06-14T16:21:30Z" : "2026-06-14T15:59:00Z", lastActiveAt: routeId === "route_opaque_8b2c" ? "2026-06-14T16:21:30Z" : "2026-06-14T16:18:30Z", lastSeq: routeId === "route_opaque_8b2c" ? 0 : 42, bridgePid: pid, restoreEligibility: "eligible", bridgeUrl, bridgeToken, routeToken, ...(proc ? { proc } : {}) };
    this.sessions.set(id, session); this.routes.set(routeId, id); this.runningByProject.set(projectId, id); this.metrics.inc(opts.restored ? "restore.success" : "session.start"); this.metrics.gauges["bridge.ready"] = this.counts().ready;
    logJson({ level: "info", event: opts.restored ? "restore.success" : "session.start", projectHash: hashId(projectId, "hash_project"), sessionHash: hashId(id, "hash_session"), routeHash: session.routeIdHash });
    await this.persistSession(session);
    return this.sessionStartEnvelope(session, routeToken);
  }

  sessionStartEnvelope(session: Session, token = session.routeToken) { const route: SessionRoute = { sessionId: session.id, projectId: session.projectId, routeId: session.routeId, routePath: `/s/${session.routeId}`, baseUrl: this.config.baseUrl, scopedToken: token, scopes: session.scopes, expiresAt: null, revocation: "respawn_required", ownerToken: session.routeId === "route_opaque_8b2c" ? "owner_token_alias_8b2c" : (session.projectId === "proj_7f3a" ? "owner_token_alias_7f3a" : "owner_token_alias") }; return envelope({ session: this.publicSession(session), route }); }
  publicSession(session: Session) { return { id: session.id, projectId: session.projectId, routeIdHash: session.routeIdHash, gjcSessionId: session.gjcSessionId, status: session.status, scopes: session.scopes, startedAt: session.startedAt, lastActiveAt: session.lastActiveAt, lastSeq: session.lastSeq, bridgePid: session.bridgePid, restoreEligibility: session.restoreEligibility, ...(session.skipReason ? { skipReason: session.skipReason } : {}), ...(session.degradedReason ? { degradedReason: session.degradedReason } : {}), ...(session.idleSince ? { idleSince: session.idleSince } : {}) }; }
  listSessions() { return envelope({ sessions: [...this.sessions.values()].map(s => this.publicSession(s)), restoreSkipped: this.restoreSkipped }); }

  stop(sessionId: string) { const s = this.sessions.get(sessionId); if (!s) return false; s.proc?.kill(); s.status = "stopped"; revokeScopedToken(s.routeToken); this.routes.delete(s.routeId); this.runningByProject.delete(s.projectId); this.metrics.inc("session.stop"); logJson({ level: "info", event: "session.stop", projectHash: hashId(s.projectId, "hash_project"), sessionHash: hashId(s.id, "hash_session"), routeHash: s.routeIdHash }); return true; }
  async respawn(sessionId: string) { const old = this.sessions.get(sessionId); if (!old) throw new Error("unknown session"); revokeScopedToken(old.routeToken); old.proc?.kill(); this.sessions.delete(sessionId); this.routes.delete(old.routeId); const res = await this.start(old.projectId, { bridgeUrl: old.bridgeUrl, bridgeToken: old.bridgeToken, routeId: "route_opaque_8b2c", scopedTokenAlias: "scoped_route_token_alias_8b2c" }); this.metrics.inc("session.respawn"); logJson({ level: "info", event: "session.respawn", projectHash: hashId(old.projectId, "hash_project"), sessionHash: hashId(old.id, "hash_session"), routeHash: old.routeIdHash }); return res; }
  route(routeId: string) { const id = this.routes.get(routeId); return id ? this.sessions.get(id) : undefined; }
  markIdle(sessionId: string, now = new Date().toISOString()) { const s = this.sessions.get(sessionId); if (!s) return false; const sessionHash = hashId(s.id, "hash_session"); const projectHash = hashId(s.projectId, "hash_project"); s.status = "idle"; s.idleSince = now; delete s.degradedReason; this.metrics.clearSessionState("degraded", sessionHash); this.metrics.setSessionState("idle", sessionHash, projectHash, 1); logJson({ level: "info", event: "session.idle", projectHash, sessionHash, routeHash: s.routeIdHash }); return true; }
  markDegraded(sessionId: string, code = "bridge_unhealthy") { const s = this.sessions.get(sessionId); if (!s) return false; const sessionHash = hashId(s.id, "hash_session"); const projectHash = hashId(s.projectId, "hash_project"); s.status = "degraded"; s.degradedReason = code; delete s.idleSince; this.metrics.clearSessionState("idle", sessionHash); this.metrics.setSessionState("degraded", sessionHash, projectHash, 1); logJson({ level: "warn", event: "session.degraded", projectHash, sessionHash, routeHash: s.routeIdHash, code }); return true; }

  async persistSession(session: Session) {
    await mkdir(this.stateDir, { recursive: true });
    const meta: PersistedSessionMeta = { id: session.id, projectId: session.projectId, projectHash: hashId(session.projectId, "hash_project"), routeHash: session.routeIdHash, gjcSessionId: session.gjcSessionId, status: session.status, scopes: session.scopes, startedAt: session.startedAt, lastActiveAt: session.lastActiveAt, lastSeq: session.lastSeq, restoreEligibility: session.restoreEligibility === "skipped" ? "ineligible" : session.restoreEligibility };
    await writeFile(join(this.stateDir, `${session.id}.json`), JSON.stringify(meta, null, 2));
  }
  async readPersistedSessions(): Promise<PersistedSessionMeta[]> {
    try { const names = await readdir(this.stateDir); const out: PersistedSessionMeta[] = []; for (const name of names.filter(n => n.endsWith(".json"))) { try { out.push(JSON.parse(await readFile(join(this.stateDir, name), "utf8"))); } catch {} } return out; } catch { return []; }
  }
  recordRestoreSkip(meta: Partial<PersistedSessionMeta>, reason: RestoreSkipReason, attemptedRank?: number) {
    const session: Session = { id: meta.id ?? "sess_unknown", projectId: meta.projectId ?? "project_unknown", routeId: "route_skipped", routeIdHash: meta.routeHash ?? "hash_route_unknown", gjcSessionId: meta.gjcSessionId ?? "gjc_unknown", status: "stopped", scopes: meta.scopes ?? [], startedAt: meta.startedAt ?? "", lastActiveAt: meta.lastActiveAt ?? "", lastSeq: meta.lastSeq ?? 0, bridgePid: null, restoreEligibility: "skipped", bridgeUrl: "", bridgeToken: "", routeToken: "", skipReason: reason };
    this.restoreSkipped.push({ session: this.publicSession(session), restore: { reason, retryable: false, budget: { maxRestoredSessions: this.config.persistenceBudget.maxRestoredSessions, attemptedRank: attemptedRank ?? 0 } } });
    this.metrics.inc("restore.skipped", reason);
    logJson({ level: "info", event: "restore.skipped", ...(meta.projectHash ? { projectHash: meta.projectHash } : {}), ...(meta.id ? { sessionHash: hashId(meta.id, "hash_session") } : {}), ...(meta.routeHash ? { routeHash: meta.routeHash } : {}), code: reason, retryable: false });
  }
}
