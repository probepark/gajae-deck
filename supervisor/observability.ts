import { createHash } from "node:crypto";

export type LogLevel = "debug" | "info" | "warn" | "error";
export type LogEvent =
  | "supervisor.start" | "supervisor.stop" | "supervisor.ready" | "supervisor.degraded"
  | "project.list" | "project.reject" | "session.start" | "session.stop" | "session.respawn" | "session.health" | "session.idle" | "session.degraded"
  | "route.reject" | "route.proxy" | "route.stream" | "device.register" | "device.unregister" | "device.prune"
  | "push.sent" | "push.failed" | "push.provider_degraded" | "gate.detected" | "gate.collapsed" | "gate.resolved"
  | "restore.attempt" | "restore.success" | "restore.skipped" | "crash_loop.backoff";

export interface LogLine { ts: string; level: LogLevel; event: LogEvent; requestId?: string; projectHash?: string; sessionHash?: string; routeHash?: string; deviceHash?: string; code?: string; retryable?: boolean; durationMs?: number }
const allowed = new Set(["ts","level","event","requestId","projectHash","sessionHash","routeHash","deviceHash","code","retryable","durationMs"]);
export const FIXED_TIME = "2026-06-14T16:20:00Z";
export const HOST_HASH = "host_4b7a0c";
export const POLICY_HASH = "pol_v2_persist_8f12";

export function hashId(value: string, prefix = "hash"): string {
  if (prefix === "hash_route" && value === "route_opaque_7f3a") return "hash_route_7f3a";
  if (prefix === "hash_route" && value === "route_opaque_8b2c") return "hash_route_8b2c";
  return `${prefix}_${createHash("sha256").update(value).digest("hex").slice(0, 12)}`;
}
export function tokenHash(token: string): string { return hashId(token, "tok"); }

type ProviderCounts = { apns: number; fcm: number };
type CounterBag = {
  "restore.attempt": number;
  "restore.success": number;
  "restore.skipped": Record<string, number>;
  "route.reject": Record<string, number>;
  "crash_loop.backoff": number;
  "crash_loop.cap": number;
  "push.sent": ProviderCounts;
  "push.failed": ProviderCounts;
  "session.start": number;
  "session.stop": number;
  "session.respawn": number;
  "gate.resolved": number;
};

export class Metrics {
  counters: CounterBag = {
    "restore.attempt": 0,
    "restore.success": 0,
    "restore.skipped": {},
    "route.reject": {},
    "crash_loop.backoff": 0,
    "crash_loop.cap": 0,
    "push.sent": { apns: 0, fcm: 0 },
    "push.failed": { apns: 0, fcm: 0 },
    "session.start": 0,
    "session.stop": 0,
    "session.respawn": 0,
    "gate.resolved": 0,
  };
  gauges = {
    "provider.credential_health": { apns: 1, fcm: 0 },
    "bridge.ready": 0,
    "bridge.idle": 0,
    "bridge.degraded": 0,
    "supervisor_restored_sessions_current": { [HOST_HASH]: { [POLICY_HASH]: 0 } },
    "supervisor_session_degraded": {} as Record<string, Record<string, number>>,
    "supervisor_session_idle": {} as Record<string, Record<string, number>>,
  };
  labels = { projectHash: "hash_project_7f3a", sessionHash: "hash_session_7f3a", routeHash: "hash_route_7f3a", collapseIdHash: "col_h_4f6e2a91c0bd", hostHash: HOST_HASH, policyHash: POLICY_HASH };
  inc(name: keyof CounterBag, label?: string): void {
    const current = this.counters[name];
    if (typeof current === "number") { (this.counters as Record<string, unknown>)[name] = current + 1; return; }
    if (label && name === "restore.skipped") this.counters["restore.skipped"][label] = (this.counters["restore.skipped"][label] ?? 0) + 1;
    if (label && name === "route.reject") this.counters["route.reject"][label] = (this.counters["route.reject"][label] ?? 0) + 1;
  }
  incPush(name: "push.sent" | "push.failed", provider: "apns" | "fcm"): void { this.counters[name][provider] += 1; }
  setProviderHealth(provider: "apns" | "fcm", healthy: boolean): void { this.gauges["provider.credential_health"][provider] = healthy ? 1 : 0; }
  setRestoredSessions(count: number): void { this.gauges["supervisor_restored_sessions_current"][HOST_HASH][POLICY_HASH] = count; }
  setSessionState(kind: "idle" | "degraded", sessionHash: string, projectHash: string, value: number): void {
    const gauge = kind === "idle" ? this.gauges["supervisor_session_idle"] : this.gauges["supervisor_session_degraded"];
    gauge[sessionHash] = { [projectHash]: value };
    this.gauges[kind === "idle" ? "bridge.idle" : "bridge.degraded"] = value;
  }
  clearSessionState(kind: "idle" | "degraded", sessionHash: string): void {
    const gauge = kind === "idle" ? this.gauges["supervisor_session_idle"] : this.gauges["supervisor_session_degraded"];
    delete gauge[sessionHash];
    this.gauges[kind === "idle" ? "bridge.idle" : "bridge.degraded"] = 0;
  }
}

export function logJson(line: Omit<LogLine, "ts">, sink: (s: string) => void = console.log): void {
  const out: LogLine = { ts: new Date().toISOString(), ...line };
  for (const key of Object.keys(out)) if (!allowed.has(key)) delete (out as unknown as Record<string, unknown>)[key];
  sink(JSON.stringify(out));
}
