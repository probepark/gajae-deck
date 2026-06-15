import type { Metrics } from "./observability";
import { FIXED_TIME } from "./observability";
import type { SessionRegistry } from "./sessions";

export const requestId = "req_01JSAFE000000000000000000";
export function envelope<T extends object>(body: T) { return { schemaVersion: 1, requestId, serverTime: FIXED_TIME, ...body }; }
export function healthResponse(registry?: SessionRegistry) {
  const counts = registry?.counts() ?? { ready: 1, idle: 1, degraded: 0, stopped: 1 };
  return envelope({ status: "degraded", supervisor: { status: "ok", versionAlias: "supervisor_version_alias" }, config: { status: "ok" }, provider: { status: "degraded", code: "provider_unhealthy" }, store: { status: "ok" }, bridges: counts });
}
export function readyResponse() { return envelope({ ready: true, checks: { config: true, stateLock: true, listener: true } }); }
export function metricsResponse(metrics: Metrics) { return envelope({ counters: metrics.counters, gauges: metrics.gauges, labels: metrics.labels }); }
export function json(data: unknown, status = 200): Response { return Response.json(data, { status, headers: { "content-type": "application/json" } }); }
export function errorResponse(code: string, message: string, retryable: boolean, details: Record<string, unknown>, status: number): Response { return json(envelope({ error: { code, message, retryable, details } }), status); }
