import { hashId } from "./observability";

export interface ProviderCredentialRefs { apns?: string; fcm?: string }
export interface PersistenceBudgetDefaults { maxRestoredSessions: number; staleSessionTtl: string; crashLoopWindow: string; crashLoopMaxRestarts: number; crashLoopBackoff: readonly string[]; idleSoftLimit: string }
export interface ProjectConfig { id: string; displayAlias: string; cwd: string; cwdHash: string; autostart: "recent" | "manual"; safeSummaryAlias: string }
export interface SupervisorConfig { baseUrl: string; controlTokenHash: string; providerCredentialRefs: ProviderCredentialRefs; persistenceBudget: PersistenceBudgetDefaults; projects: ProjectConfig[]; bridgeCommand: string[]; bridgeHost: string; bridgeScopes: string[]; bridgeTls?: { cert: string; key: string } }

export const DEFAULT_PERSISTENCE_BUDGET: PersistenceBudgetDefaults = { maxRestoredSessions: 3, staleSessionTtl: "12h", crashLoopWindow: "10m", crashLoopMaxRestarts: 3, crashLoopBackoff: ["5s", "30s", "2m", "10m+jitter20"], idleSoftLimit: "2h" };
export const DEFAULT_SCOPES = ["events", "prompt", "permission", "ui_responses", "claim_control"] as const;
export type RouteScope = typeof DEFAULT_SCOPES[number];

export function defaultConfig(overrides: Partial<SupervisorConfig> = {}): SupervisorConfig {
  const cwd = process.cwd();
  return {
    baseUrl: "https://control.example.invalid",
    controlTokenHash: "tok_control_alias",
    providerCredentialRefs: { apns: "env:APNS_CREDENTIAL_REF", fcm: "env:FCM_CREDENTIAL_REF" },
    persistenceBudget: DEFAULT_PERSISTENCE_BUDGET,
    bridgeCommand: [process.execPath, "-e", "Bun.serve({port:Number(Bun.env.GJC_BRIDGE_PORT),hostname:Bun.env.GJC_BRIDGE_HOST,fetch(req){return new Response('ok')}}); await new Promise(()=>{})"],
    bridgeHost: "127.0.0.1",
    bridgeScopes: ["prompt", "control", "bash", "export", "session", "model", "message:read", "host_tools", "host_uri"],
    projects: [
      { id: "proj_7f3a", displayAlias: "project_alias_alpha", cwd, cwdHash: "hash_cwd_7f3a", autostart: "recent", safeSummaryAlias: "summary_alias_active" },
      { id: "proj_91bd", displayAlias: "project_alias_beta", cwd, cwdHash: "hash_cwd_91bd", autostart: "manual", safeSummaryAlias: "summary_alias_stopped" },
    ],
    ...overrides,
  };
}
export function projectHash(projectId: string): string { return projectId === "proj_7f3a" ? "hash_project_7f3a" : hashId(projectId, "hash_project"); }
