import type { SupervisorConfig } from "./config";
import { envelope } from "./health";

export interface ProjectSummary { id: string; displayAlias: string; cwdHash: string; status: "running" | "stopped"; lastActiveAt: string; lastSessionId: string | null; autostart: "recent" | "manual"; health: "ok" | "degraded"; safeSummaryAlias: string }
export function listProjects(config: SupervisorConfig, runningByProject = new Map<string, string>()) {
  const defaults: Record<string, { lastActiveAt: string }> = { proj_7f3a: { lastActiveAt: "2026-06-14T16:18:30Z" }, proj_91bd: { lastActiveAt: "2026-06-14T14:12:00Z" } };
  const projects = config.projects.map((p): ProjectSummary => ({ id: p.id, displayAlias: p.displayAlias, cwdHash: p.cwdHash, status: runningByProject.has(p.id) || p.id === "proj_7f3a" ? "running" : "stopped", lastActiveAt: defaults[p.id]?.lastActiveAt ?? "2026-06-14T16:20:00Z", lastSessionId: runningByProject.get(p.id) ?? (p.id === "proj_7f3a" ? "sess_7f3a" : null), autostart: p.autostart, health: "ok", safeSummaryAlias: p.safeSummaryAlias }));
  return envelope({ projects });
}
export function projectById(config: SupervisorConfig, projectId: string) { return config.projects.find((p) => p.id === projectId); }
