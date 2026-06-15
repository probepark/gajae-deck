import { access } from "node:fs/promises";
import { defaultConfig, projectHash, type SupervisorConfig } from "./config";
import { SessionRegistry, type PersistedSessionMeta, type RestoreSkipReason } from "./sessions";
import { HOST_HASH, POLICY_HASH, logJson, Metrics, hashId, type Metrics as MetricsType } from "./observability";

export interface BootResult { ready: boolean; restoredSessions: number; skipped: readonly string[]; restore: { attempted: number; restored: number; skipped: number; hostHash: string; policyHash: string } }

function durationMs(value: string): number {
  const match = /^(\d+)(s|m|h)$/.exec(value);
  if (!match) return 0;
  const n = Number(match[1]);
  return n * (match[2] === "h" ? 3_600_000 : match[2] === "m" ? 60_000 : 1_000);
}

function isRecent(iso: string, nowMs: number, ttl: string): boolean { return nowMs - Date.parse(iso) <= durationMs(ttl); }
async function cwdAvailable(config: SupervisorConfig, projectId: string): Promise<boolean> {
  const project = config.projects.find(p => p.id === projectId);
  if (!project) return false;
  try { await access(project.cwd); return true; } catch { return false; }
}
function crashLoopCapped(meta: PersistedSessionMeta, nowMs: number, window: string, max: number): boolean {
  const restarts = (meta.crashRestarts ?? []).filter(t => nowMs - Date.parse(t) <= durationMs(window));
  return restarts.length >= max;
}

export async function restoreWithinBudget(options: { config?: SupervisorConfig; registry?: SessionRegistry; metrics?: MetricsType; now?: Date } = {}): Promise<BootResult> {
  const config = options.config ?? defaultConfig();
  const registry = options.registry ?? new SessionRegistry(config, options.metrics ?? new Metrics());
  const budget = config.persistenceBudget;
  const now = options.now ?? new Date();
  const nowMs = now.getTime();
  const metas = (await registry.readPersistedSessions()).sort((a, b) => Date.parse(b.lastActiveAt) - Date.parse(a.lastActiveAt));
  const skipped: RestoreSkipReason[] = [];
  let restored = 0;
  for (let i = 0; i < metas.length; i++) {
    const meta = metas[i]!;
    logJson({ level: "info", event: "restore.attempt" });
    registry.metrics.inc("restore.attempt");
    let reason: RestoreSkipReason | undefined;
    if (!config.projects.some(p => p.id === meta.projectId)) reason = "project_missing";
    else if (!(await cwdAvailable(config, meta.projectId))) reason = "cwd_unavailable";
    else if (!isRecent(meta.lastActiveAt, nowMs, budget.staleSessionTtl)) reason = "stale_ttl";
    else if (crashLoopCapped(meta, nowMs, budget.crashLoopWindow, budget.crashLoopMaxRestarts)) reason = "crash_loop_cap";
    else if (restored >= budget.maxRestoredSessions) reason = "max_restored_sessions";

    if (reason) {
      skipped.push(reason);
      registry.recordRestoreSkip(meta, reason, i + 1);
      if (reason === "crash_loop_cap") {
        registry.metrics.inc("crash_loop.cap");
        registry.metrics.inc("crash_loop.backoff");
        logJson({ level: "info", event: "crash_loop.backoff", projectHash: projectHash(meta.projectId), sessionHash: hashId(meta.id, "hash_session") });
      }
      continue;
    }
    await registry.start(meta.projectId, { ...(meta.routeId ? { routeId: meta.routeId } : {}), restored: true });
    restored++;
  }
  registry.metrics.setRestoredSessions(restored);
  return { ready: true, restoredSessions: restored, skipped, restore: { attempted: metas.length, restored, skipped: skipped.length, hostHash: HOST_HASH, policyHash: POLICY_HASH } };
}
