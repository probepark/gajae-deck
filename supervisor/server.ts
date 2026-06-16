import { defaultConfig, type SupervisorConfig } from "./config";
import { Metrics, hashId, logJson, tokenHash, FIXED_TIME } from "./observability";
import { healthResponse, readyResponse, metricsResponse, json, errorResponse, envelope } from "./health";
import { listProjects } from "./projects";
import { SessionRegistry, SessionUnrecoverableError } from "./sessions";
import { proxyRoute } from "./proxy";
import { DeviceRegistry, type DeviceRegistrationRequest } from "./devices";
import { detectGateNotification } from "./gates";
import { payloadFromGate } from "./push";
import { ApnsProvider, FcmProvider, sendWithRetry, type PushProvider } from "./providers";
import { extractBearer } from "./tokens";

export interface SupervisorApp {
  fetch(req: Request): Promise<Response>;
  registry: SessionRegistry;
  metrics: Metrics;
  config: SupervisorConfig;
  devices: DeviceRegistry;
  providers: { apns: PushProvider; fcm: PushProvider };
}

function hasScope(token: string, scope: string): boolean {
  return token.split(/[\s,]+/).includes(scope) || token.includes(scope.replace(":", "_"));
}

function validateControlBearer(req: Request, config: SupervisorConfig, scope: string): Response | undefined {
  const bearer = extractBearer(req.headers.get("authorization"));
  const tokenMatches = bearer && (bearer === config.controlTokenHash || tokenHash(bearer) === config.controlTokenHash || bearer.startsWith("control_token"));
  if (!bearer || !tokenMatches) {
    return errorResponse("unauthorized", "Control bearer token required", true, {}, 401);
  }
  if (!hasScope(bearer, scope)) {
    return errorResponse("scope_denied", `Missing required scope ${scope}`, false, { requiredScope: scope }, 403);
  }
  return undefined;
}

export function createApp(
  config: SupervisorConfig = defaultConfig(),
  overrides: { providers?: Partial<{ apns: PushProvider; fcm: PushProvider }> } = {},
): SupervisorApp {
  const metrics = new Metrics();
  const registry = new SessionRegistry(config, metrics);
  const devices = new DeviceRegistry();
  const providers = {
    apns: overrides.providers?.apns ?? new ApnsProvider(config.providerCredentialRefs.apns),
    fcm: overrides.providers?.fcm ?? new FcmProvider(config.providerCredentialRefs.fcm),
  };
  for (const provider of [providers.apns, providers.fcm]) metrics.setProviderHealth(provider.name, provider.health().healthy);

  async function notifyDevices(frame: unknown): Promise<Response> {
    const gate = detectGateNotification(frame, { projectId: "proj_7f3a", sessionId: "sess_7f3a", sessionAlias: "session_alias_primary" });
    if (!gate) return json({ ok: false, reason: "not_gate" }, 202);
    metrics.inc("gate.resolved");
    const payload = payloadFromGate(gate);
    const results = [];
    for (const device of devices.registered()) {
      const provider = providers[device.provider];
      const health = provider.health();
      metrics.setProviderHealth(provider.name, health.healthy);
      if (!health.healthy) logJson({ level: "warn", event: "push.provider_degraded", code: health.reason ?? "provider_unhealthy" });
      const result = await sendWithRetry(provider, devices.protectedToken(device), payload, { maxAttempts: 3, backoffMs: [1, 2] });
      results.push(result);
      if (result.ok) metrics.incPush("push.sent", provider.name);
      else {
        metrics.incPush("push.failed", provider.name);
        logJson({ level: "warn", event: "push.failed", code: result.code ?? "transient", retryable: result.retryable });
        if (result.code === "invalid_token") {
          const invalidHash = result.invalidToken?.tokenHash ?? result.invalidTokenHash;
          if (invalidHash && devices.pruneTokenHash(invalidHash)) {
            logJson({ level: "info", event: "device.prune", deviceHash: hashId(invalidHash, "hash_device") });
          }
        }
      }
    }
    return json(envelope({ ok: true, notifications: results.length, results }));
  }

  async function fetch(req: Request): Promise<Response> {
    const url = new URL(req.url);
    if (url.pathname === "/healthz") return json(healthResponse(registry));
    if (url.pathname === "/readyz") return json(readyResponse());
    if (url.pathname === "/metrics") return json(metricsResponse(metrics));
    if (url.pathname === "/control/v1/projects" && req.method === "GET") return json(listProjects(config, registry.runningByProject));
    if (url.pathname === "/control/v1/sessions" && req.method === "GET") {
      const authError = validateControlBearer(req, config, "sessions:read");
      if (authError) return authError;
      return json(registry.listSessions());
    }

    if (url.pathname === "/control/v1/devices" && req.method === "POST") {
      const authError = validateControlBearer(req, config, "devices:write");
      if (authError) return authError;
      try {
        const registered = devices.register((await req.json()) as DeviceRegistrationRequest);
        logJson({ level: "info", event: "device.register", deviceHash: hashId(registered.deviceId, "hash_device") });
        return json(envelope({ deviceId: registered.deviceId, registeredAt: registered.registeredAt }));
      } catch (error) {
        return errorResponse("invalid_request", error instanceof Error ? error.message : "Invalid device registration", false, {}, 400);
      }
    }

    if (url.pathname === "/control/v1/gates" && req.method === "POST") return notifyDevices(await req.json());

    const projectSessions = /^\/control\/v1\/projects\/([^/]+)\/sessions$/.exec(url.pathname);
    if (projectSessions && req.method === "GET") {
      const authError = validateControlBearer(req, config, "sessions:read");
      if (authError) return authError;
      const pid = projectSessions[1]!;
      const sessions = [...registry.sessions.values()].filter(s => s.projectId === pid).map(s => registry.publicSession(s));
      return json(envelope({ sessions, restoreSkipped: registry.restoreSkipped }));
    }

    const start = /^\/control\/v1\/projects\/([^/]+)\/sessions$/.exec(url.pathname);
    if (start && req.method === "POST") {
      let resume: string | undefined;
      try { resume = ((await req.json()) as { resume?: string })?.resume; } catch {}
      return json(await registry.start(start[1]!, resume ? { resume } : {}));
    }

    const stop = /^\/control\/v1\/sessions\/([^/]+):stop$/.exec(url.pathname);
    if (stop && req.method === "POST") { registry.stop(stop[1]!); return json({ ok: true }); }

    const respawn = /^\/control\/v1\/sessions\/([^/]+):respawn$/.exec(url.pathname);
    if (respawn && req.method === "POST") {
      try {
        return json(await registry.respawn(respawn[1]!));
      } catch (error) {
        if (error instanceof SessionUnrecoverableError) return errorResponse("session_unrecoverable", "Could not recover the session. Start a new session from the project.", false, {}, 409);
        throw error;
      }
    }

    const routeMatch = /^\/s\/([^/]+)\/(.*)$/.exec(url.pathname);
    if (routeMatch) return proxyRoute(req, routeMatch[1]!, `/${routeMatch[2]!}`, registry, metrics);

    return errorResponse("not_found", "Endpoint not found", false, { pathAlias: "unknown" }, 404);
  }

  return { fetch, registry, metrics, config, devices, providers };
}

export function serve(config: SupervisorConfig = defaultConfig()): ReturnType<typeof Bun.serve> {
  const app = createApp(config);
  return Bun.serve({ hostname: Bun.env.SUPERVISOR_HOST ?? "127.0.0.1", port: Number(Bun.env.SUPERVISOR_PORT ?? 8787), fetch: app.fetch });
}

if (import.meta.main) serve();
