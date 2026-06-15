import { afterEach, describe, expect, test } from "bun:test";
import { mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createApp } from "../server";
import { defaultConfig } from "../config";
import { Metrics, logJson } from "../observability";
import { healthResponse, metricsResponse, readyResponse } from "../health";
import { listProjects } from "../projects";
import { assertAliasOnlyPayload, buildApnsRequest, buildDeepLinkRequest, buildFcmRequest, renderAliasOnlyPush } from "../push";
import { detectGateNotification } from "../gates";
import { DeviceRegistry } from "../devices";
import { FakePushProvider, sendWithRetry } from "../providers";
import { defaultScopes, mintScopedToken, timingSafeTokenEqual, validateRouteClaim } from "../tokens";
import { restoreWithinBudget } from "../boot";
import { SessionRegistry } from "../sessions";

const servers: ReturnType<typeof Bun.serve>[] = [];
afterEach(() => { while (servers.length) servers.pop()!.stop(true); });
async function body(res: Response) { return await res.json() as any; }
function fakeBridge(handler?: (req: Request) => Response | Promise<Response>) {
  const seen: Request[] = [];
  const server = Bun.serve({ port: 0, hostname: "127.0.0.1", fetch(req) { seen.push(req); return handler?.(req) ?? Response.json({ ok: true, authorization: req.headers.get("authorization") }); } });
  servers.push(server); return { url: `http://127.0.0.1:${server.port}`, seen };
}

describe("health fixtures", () => {
  test("health/ready/metrics have fixture shape", async () => {
    const healthFixture = await import("../../fixtures/v2/control/health.json");
    const readyFixture = await import("../../fixtures/v2/control/ready.json");
    const metricsFixture = await import("../../fixtures/v2/control/metrics.json");
    expect(Object.keys(healthResponse()).sort()).toEqual(Object.keys(healthFixture.default).sort());
    expect(readyResponse()).toEqual(readyFixture.default);
    expect(Object.keys(metricsResponse(new Metrics()).counters)).toEqual(Object.keys(metricsFixture.default.counters));
    expect(Object.keys(metricsResponse(new Metrics()).gauges)).toEqual(Object.keys(metricsFixture.default.gauges));
  });
  test("projects shape matches fixture", async () => {
    const projectsFixture = await import("../../fixtures/v2/control/projects.json");
    expect(listProjects(defaultConfig())).toEqual(projectsFixture.default as any);
  });
});

describe("sessions and proxy", () => {
  test("spawn env includes bridge contract and cwd", async () => {
    const app = createApp(defaultConfig({ bridgeCommand: [process.execPath, "-e", "await Bun.write(Bun.env.OUT, JSON.stringify({token:Bun.env.GJC_BRIDGE_TOKEN,host:Bun.env.GJC_BRIDGE_HOST,port:Bun.env.GJC_BRIDGE_PORT,endpoints:Bun.env.GJC_BRIDGE_ENDPOINTS,scopes:Bun.env.GJC_BRIDGE_SCOPES,cwd:process.cwd()})); await new Promise(()=>{})"] }));
    const out = `${process.cwd()}/test-spawn-env.json`;
    app.config.bridgeCommand = [process.execPath, "-e", `await Bun.write(${JSON.stringify(out)}, JSON.stringify({token:Bun.env.GJC_BRIDGE_TOKEN,host:Bun.env.GJC_BRIDGE_HOST,port:Bun.env.GJC_BRIDGE_PORT,endpoints:Bun.env.GJC_BRIDGE_ENDPOINTS,scopes:Bun.env.GJC_BRIDGE_SCOPES,cwd:process.cwd()})); await new Promise(()=>{})`];
    const res = await app.registry.start("proj_7f3a");
    await Bun.sleep(80);
    const env = JSON.parse(await Bun.file(out).text());
    expect(env.token).toStartWith("bridge_"); expect(env.host).toBe("127.0.0.1"); expect(env.port).toBeTruthy(); expect(env.endpoints).toBe("all"); expect(env.scopes).toContain("events"); expect(env.cwd).toBe(process.cwd());
    app.registry.stop(res.session.id); await Bun.file(out).delete();
  });
  test("route mismatch returns 403 and does not contact bridge", async () => {
    const bridge = fakeBridge(); const app = createApp();
    const start = await app.registry.start("proj_7f3a", { bridgeUrl: bridge.url });
    const wrongToken = mintScopedToken({ routeId: "bound_route", sessionId: start.session.id, projectId: "proj_7f3a", scopes: defaultScopes(), issuedAt: new Date().toISOString() }, "wrong_route_token");
    const res = await app.fetch(new Request(`http://x/s/${start.route.routeId}/v1/handshake`, { headers: { authorization: `Bearer ${wrongToken}` } }));
    expect(res.status).toBe(403); expect((await body(res)).error.code).toBe("route_token_mismatch"); expect(bridge.seen).toHaveLength(0);
  });
  test("SSE streaming is unbuffered and Authorization rewrites to bridge token", async () => {
    const bridge = fakeBridge(() => new Response(new ReadableStream({ start(controller) { controller.enqueue(new TextEncoder().encode("data: one\n\n")); setTimeout(() => { controller.enqueue(new TextEncoder().encode("data: two\n\n")); controller.close(); }, 10); } }), { headers: { "content-type": "text/event-stream" } }));
    const app = createApp(); const start = await app.registry.start("proj_7f3a", { bridgeUrl: bridge.url, bridgeToken: "bridge_secret" });
    const res = await app.fetch(new Request(`http://x/s/${start.route.routeId}/v1/sessions/gjc_sess_7f3a/events`, { headers: { authorization: `Bearer ${start.route.scopedToken}` } }));
    expect(res.headers.get("content-type")).toContain("text/event-stream"); expect(await res.text()).toBe("data: one\n\ndata: two\n\n"); expect(bridge.seen[0]!.headers.get("authorization")).toBe("Bearer bridge_secret");
  });
  test("respawn revokes previous route token", async () => {
    const bridge = fakeBridge(); const app = createApp(); const start = await app.registry.start("proj_7f3a", { bridgeUrl: bridge.url });
    const respawn = await app.registry.respawn(start.session.id);
    expect(respawn.route.scopedToken).not.toBe(start.route.scopedToken);
    expect(validateRouteClaim(start.route.scopedToken, start.route.routeId, "events").ok).toBe(false);
    expect(validateRouteClaim(respawn.route.scopedToken, respawn.route.routeId, "events").ok).toBe(true);
  });

  test("route allowlist rejects underscope and unknown endpoints before bridge contact", async () => {
    const bridge = fakeBridge();
    const app = createApp();
    const start = await app.registry.start("proj_7f3a", { bridgeUrl: bridge.url });
    const eventsOnly = mintScopedToken({ routeId: start.route.routeId, sessionId: start.session.id, projectId: "proj_7f3a", scopes: ["events"], issuedAt: new Date().toISOString() }, "events_only_route_token");
    const endpoints = [
      { path: `/s/${start.route.routeId}/v1/sessions/gjc_sess_7f3a/commands`, method: "POST" },
      { path: `/s/${start.route.routeId}/v1/sessions/gjc_sess_7f3a/host-tool-results`, method: "POST" },
      { path: `/s/${start.route.routeId}/v1/sessions/gjc_sess_7f3a/claim-control`, method: "POST" },
      { path: `/s/${start.route.routeId}/v1/sessions/gjc_sess_7f3a/disconnect-control`, method: "POST" },
      { path: `/s/${start.route.routeId}/v1/sessions/gjc_sess_7f3a/unknown`, method: "POST" },
    ];
    for (const endpoint of endpoints) {
      const res = await app.fetch(new Request(`http://x${endpoint.path}`, { method: endpoint.method, headers: { authorization: `Bearer ${eventsOnly}` } }));
      expect(res.status).toBe(403);
      expect((await body(res)).error.code).toBe("scope_denied");
    }
    expect(bridge.seen).toHaveLength(0);
  });

  test("control stop and respawn use colon action routes", async () => {
    const bridge = fakeBridge();
    const app = createApp();
    const start = await app.registry.start("proj_7f3a", { bridgeUrl: bridge.url });
    const stop = await app.fetch(new Request(`http://x/control/v1/sessions/${start.session.id}:stop`, { method: "POST" }));
    expect(stop.status).toBe(200);
    expect((await body(stop)).ok).toBe(true);
    const oldStop = await app.fetch(new Request(`http://x/control/v1/sessions/${start.session.id}/stop`, { method: "POST" }));
    expect(oldStop.status).toBe(404);

    const second = await app.registry.start("proj_7f3a", { bridgeUrl: bridge.url });
    const respawn = await app.fetch(new Request(`http://x/control/v1/sessions/${second.session.id}:respawn`, { method: "POST" }));
    expect(respawn.status).toBe(200);
    expect((await body(respawn)).route.routePath).toBe("/s/route_opaque_8b2c");
    const oldRespawn = await app.fetch(new Request(`http://x/control/v1/sessions/${second.session.id}/respawn`, { method: "POST" }));
    expect(oldRespawn.status).toBe(404);
  });

  test("session start and respawn responses match control fixtures", async () => {
    const startFixture = (await import("../../fixtures/v2/control/session-start.json")).default as any;
    const respawnFixture = (await import("../../fixtures/v2/control/session-respawn.json")).default as any;
    const app = createApp();
    const start = await app.registry.start("proj_7f3a", { bridgeUrl: "http://127.0.0.1:1", routeId: "route_opaque_7f3a", scopedTokenAlias: "scoped_route_token_alias_7f3a" });
    expect(start).toEqual(startFixture);
    const respawn = await app.registry.respawn(start.session.id);
    expect(respawn).toEqual(respawnFixture);
  });
});


describe("boot", () => {
  async function fakeRegistry(entries: unknown[], config = defaultConfig()) {
    const dir = await mkdtemp(join(tmpdir(), "gjc-supervisor-state-"));
    for (const entry of entries as any[]) await writeFile(join(dir, `${entry.id}.json`), JSON.stringify(entry));
    const metrics = new Metrics();
    return { registry: new SessionRegistry(config, metrics, dir), metrics, dir, config };
  }
  const recent = (id: string, n = 0) => ({ id, projectId: "proj_7f3a", projectHash: "hash_project_7f3a", routeHash: `hash_route_${id}`, routeId: `route_${id}`, gjcSessionId: `gjc_${id}`, status: "ready" as const, scopes: defaultScopes(), startedAt: "2026-06-14T15:00:00Z", lastActiveAt: `2026-06-14T16:1${n}:00Z`, lastSeq: n, restoreEligibility: "eligible" });

  test("restores recent sessions inside max budget and records metrics", async () => {
    const { registry, metrics, config } = await fakeRegistry([recent("sess_a", 1), recent("sess_b", 2), recent("sess_c", 3)]);
    const result = await restoreWithinBudget({ config, registry, now: new Date("2026-06-14T16:20:00Z") });
    expect(result.restoredSessions).toBe(3);
    expect(result.skipped).toEqual([]);
    expect(metrics.counters["restore.attempt"]).toBe(3);
    expect(metrics.counters["restore.success"]).toBe(3);
    expect(metrics.gauges["supervisor_restored_sessions_current"].host_4b7a0c.pol_v2_persist_8f12).toBe(3);
  });

  test("skips stale sessions by ttl", async () => {
    const stale = { ...recent("sess_stale"), lastActiveAt: "2026-06-14T04:18:00Z" };
    const { registry, config } = await fakeRegistry([stale]);
    const result = await restoreWithinBudget({ config, registry, now: new Date("2026-06-14T16:20:00Z") });
    expect(result.skipped).toEqual(["stale_ttl"]);
    expect(registry.restoreSkipped[0]!.restore.reason).toBe("stale_ttl");
  });

  test("skips overflow above max restored sessions", async () => {
    const { registry, config } = await fakeRegistry([recent("sess_1", 1), recent("sess_2", 2), recent("sess_3", 3), recent("sess_4", 4)]);
    const result = await restoreWithinBudget({ config, registry, now: new Date("2026-06-14T16:20:00Z") });
    expect(result.restoredSessions).toBe(3);
    expect(result.skipped).toEqual(["max_restored_sessions"]);
  });

  test("skips crash-loop capped sessions", async () => {
    const crashing = { ...recent("sess_crash"), crashRestarts: ["2026-06-14T16:11:00Z", "2026-06-14T16:12:00Z", "2026-06-14T16:13:00Z"] };
    const { registry, metrics, config } = await fakeRegistry([crashing]);
    const result = await restoreWithinBudget({ config, registry, now: new Date("2026-06-14T16:20:00Z") });
    expect(result.skipped).toEqual(["crash_loop_cap"]);
    expect(metrics.counters["crash_loop.cap"]).toBe(1);
  });

  test("exposes degraded and idle session transitions in control list", async () => {
    const app = createApp(defaultConfig());
    const started = await app.registry.start("proj_7f3a", { bridgeUrl: "http://127.0.0.1:1" });
    app.registry.markIdle(started.session.id, "2026-06-14T18:20:00Z");
    app.registry.markDegraded(started.session.id, "bridge_unhealthy");
    const res = await app.fetch(new Request("http://x/control/v1/sessions", { headers: { authorization: "Bearer control_token_sessions_read" } }));
    const payload = await body(res);
    expect(payload.sessions[0].status).toBe("degraded");
    expect(payload.sessions[0].degradedReason).toBe("bridge_unhealthy");
  expect(app.metrics.gauges["supervisor_session_idle"][`hash_session_${started.session.id === "sess_7f3a" ? "7f3a" : ""}`] ?? Object.values(app.metrics.gauges["supervisor_session_idle"])[0]).toBeUndefined();
  });
});

describe("tokens and redaction", () => {
  test("timing safe compare and route claim validation", () => {
    const token = mintScopedToken({ routeId: "r1", sessionId: "s1", projectId: "p1", scopes: ["events"], issuedAt: "now" }, "tok_r1");
    expect(timingSafeTokenEqual("same", "same")).toBe(true); expect(timingSafeTokenEqual("same", "diff")).toBe(false);
    expect(validateRouteClaim(token, "r1", "events").ok).toBe(true); expect(validateRouteClaim(token, "r1", "prompt").ok).toBe(false); expect(validateRouteClaim(token, "r2", "events").ok).toBe(false);
  });
  test("push renderer and logs contain aliases/hashes only", () => {
    const secretCorpus = [process.cwd(), "route_secret", "control_secret", "bridge_secret", "device_secret", "My Repo", "prompt text"];
    const payload = renderAliasOnlyPush({ sessionId: "sess_secret", projectId: "proj_secret", gateKind: "permission_request", frameId: "frame_alias" });
    const lines: string[] = []; logJson({ level: "info", event: "push.sent", sessionHash: "hash_session_safe", routeHash: "hash_route_safe", code: "started" }, s => lines.push(s));
    const artifact = JSON.stringify(payload) + lines.join("\n");
    for (const secret of secretCorpus) expect(artifact.includes(secret)).toBe(false);
    expect(payload.safeTitleAlias).toBe("input_needed"); expect(payload.deepLinkOpaqueId).toStartWith("dl_opaque_");
  });
});


describe("phase 3 push backend", () => {
  test("gate detection creates alias notification", () => {
    const gate = detectGateNotification({ type: "permission_request", sessionId: "sess_secret", projectId: "proj_secret", frameId: "frame_alias", prompt: "plaintext prompt" }, { projectId: "proj_fallback", sessionId: "sess_fallback" });
    expect(gate?.gateKind).toBe("permission_request");
    const payload = renderAliasOnlyPush(gate!);
    assertAliasOnlyPayload(payload, ["sess_secret", "proj_secret", "plaintext prompt", "gajaedeck://notification/open"]);
    expect(payload.safeTitleAlias).toBe("input_needed");
    expect(payload.safeBodyAlias).toBe("open_app_to_continue");
    expect(payload.ttlSeconds).toBe(900);
  });

  test("device registration stores protected metadata and control endpoint shape", async () => {
    const app = createApp();
    const request = { installId: "install_secret", platform: "ios", environment: "sandbox", pushToken: "device_secret", appVersion: "1", locale: "ko-KR", capabilities: ["push"] };
    const res = await app.fetch(new Request("http://x/control/v1/devices", { method: "POST", headers: { authorization: "Bearer control_token_devices_write" }, body: JSON.stringify(request) }));
    expect(res.status).toBe(200);
    const registered = await body(res);
    expect(registered.deviceId).toStartWith("dev_");
    expect(registered.registeredAt).toBeTruthy();
    const stored = app.devices.get(registered.deviceId)!;
    expect(stored.tokenHash).toStartWith("tok_");
    expect(JSON.stringify(stored).includes(request.pushToken)).toBe(false);
  });

  test("fake provider retries transient failures and prunes invalid tokens", async () => {
    const registry = new DeviceRegistry();
    registry.register({ installId: "install_secret", platform: "ios", environment: "sandbox", pushToken: "device_secret", appVersion: "1", capabilities: ["push"] });
    const fake = new FakePushProvider("apns");
    fake.enqueueFailure("transient", true);
    const payload = { publicPayload: renderAliasOnlyPush({ sessionId: "sess", projectId: "proj", gateKind: "workflow_gate" }), collapseId: "hash_collapse_retry", ttlSeconds: 900 as const, priority: "high" as const };
    const retry = await sendWithRetry(fake, "device_secret", payload, { maxAttempts: 3, backoffMs: [1] });
    expect(retry.ok).toBe(true);
    expect(fake.sends).toHaveLength(2);

    fake.enqueueFailure("invalid_token", false);
    const invalid = await sendWithRetry(fake, "device_secret", payload, { maxAttempts: 3, backoffMs: [1] });
    expect(invalid.code).toBe("invalid_token");
    expect(registry.pruneTokenHash(invalid.invalidTokenHash!)).toBe(true);
    expect(registry.registered()).toHaveLength(0);
  });

  test("gate endpoint sends alias-only payload through fake provider and scans negative corpus", async () => {
    const fake = new FakePushProvider("apns");
    const app = createApp(defaultConfig(), { providers: { apns: fake } });
    const deviceRequest = { installId: "install_secret", platform: "ios", environment: "sandbox", pushToken: "device_secret", appVersion: "1", capabilities: ["push"] };
    await app.fetch(new Request("http://x/control/v1/devices", { method: "POST", headers: { authorization: "Bearer control_token_devices_write" }, body: JSON.stringify(deviceRequest) }));
    const res = await app.fetch(new Request("http://x/control/v1/gates", { method: "POST", body: JSON.stringify({ type: "ui_request", sessionId: "sess_secret", projectId: "proj_secret", frameId: "frame_secret", cwd: process.cwd(), prompt: "prompt text" }) }));
    expect(res.status).toBe(200);
    expect(fake.sends).toHaveLength(1);
    const negative = (await import("../../fixtures/v2/push/negative-corpus.json")).default as any;
    const corpusValues = String(negative.forbiddenPlaintextClasses).split("\n").slice(1).map((line: string) => line.split(",").slice(1).join(",")).filter(Boolean);
    const forbidden = [...corpusValues, process.cwd(), "sess_secret", "proj_secret", "prompt text", "device_secret"];
    assertAliasOnlyPayload(fake.sends[0]!.payload, forbidden);
    expect(app.metrics.counters["gate.resolved"]).toBe(1);
    expect(app.metrics.counters["push.sent"].apns).toBe(1);
  });
});


describe("G007 blocker regression coverage", () => {
  const auth = { authorization: "Bearer control_token_devices_write" };

  test("public push DTO and provider requests match v2 fixtures without extra keys", async () => {
    const publicPayload = renderAliasOnlyPush({
      sessionId: "sess_7f3a",
      projectId: "proj_7f3a",
      sessionAlias: "session_alias_primary",
      gateKind: "permission_request",
      frameId: "frame_7f3a",
      notificationId: "ntf_opaque_01JZ0J4M9K8W7Y6X5V4T3S2R1Q",
      collapseIdHash: "col_h_4f6e2a91c0bd",
      threadIdHash: "thr_h_9db028d21e0a",
      deepLinkOpaqueId: "dl_opaque_01JZ0J4N0P9Q8R7S6T5V4W3X2Y",
      createdAt: "2026-06-14T16:20:00Z",
    });
    expect(Object.keys(publicPayload).sort()).toEqual([
      "category",
      "collapseIdHash",
      "createdAtBucket",
      "deepLinkOpaqueId",
      "notificationId",
      "safeBodyAlias",
      "safeTitleAlias",
      "schemaVersion",
      "threadIdHash",
    ].sort());

    const providerPayload = { publicPayload, collapseIdHash: publicPayload.collapseIdHash, ttlSeconds: 900 as const, priority: "high" as const };
    const apnsFixture = await Bun.file("../fixtures/v2/push/apns.json").json();
    const fcmFixture = await Bun.file("../fixtures/v2/push/fcm.json").json();
    const deepLinkFixture = await Bun.file("../fixtures/v2/push/deeplink.json").json();

    expect({ schemaVersion: 1, requestId: apnsFixture.requestId, serverTime: apnsFixture.serverTime, ...buildApnsRequest(providerPayload, "io.devnogari.gajaedeck", apnsFixture.serverTime) }).toEqual(apnsFixture);
    expect({ schemaVersion: 1, requestId: fcmFixture.requestId, serverTime: fcmFixture.serverTime, ...buildFcmRequest("device_h_6d5a4c3b2a10", providerPayload, fcmFixture.serverTime) }).toEqual(fcmFixture);
    expect({ schemaVersion: 1, requestId: deepLinkFixture.requestId, serverTime: deepLinkFixture.serverTime, ...buildDeepLinkRequest(providerPayload) }).toEqual(deepLinkFixture);
  });

  test("device registration requires devices:write and returns standard control envelope", async () => {
    const app = createApp();
    const request = { installId: "install_secret", platform: "ios", environment: "sandbox", pushToken: "device_secret", appVersion: "1", capabilities: ["push"] };
    const denied = await app.fetch(new Request("http://x/control/v1/devices", { method: "POST", headers: { authorization: "Bearer control_token" }, body: JSON.stringify(request) }));
    expect(denied.status).toBe(403);
    const invalid = await app.fetch(new Request("http://x/control/v1/devices", { method: "POST", headers: auth, body: JSON.stringify({ ...request, platform: "web" }) }));
    expect(invalid.status).toBe(400);
    const ok = await app.fetch(new Request("http://x/control/v1/devices", { method: "POST", headers: auth, body: JSON.stringify(request) }));
    expect(ok.status).toBe(200);
    const fixture = await Bun.file("../fixtures/v2/control/device-register.json").json();
    const actual = await body(ok);
    expect(Object.keys(actual).sort()).toEqual(Object.keys(fixture).sort());
    expect(actual.schemaVersion).toBe(fixture.schemaVersion);
    expect(actual.requestId).toBe(fixture.requestId);
    expect(actual.serverTime).toBe(fixture.serverTime);
    expect(actual.deviceId).toStartWith("dev_");
    expect(actual.registeredAt).toBe(fixture.registeredAt);
  });

  test("gate endpoint prunes the registered device when provider returns exact invalid token hash", async () => {
    const fake = new FakePushProvider("apns");
    fake.enqueueFailure("invalid_token", false);
    const app = createApp(defaultConfig(), { providers: { apns: fake } });
    const request = { installId: "install_secret", platform: "ios", environment: "sandbox", pushToken: "device_secret", appVersion: "1", capabilities: ["push"] };
    const registration = await body(await app.fetch(new Request("http://x/control/v1/devices", { method: "POST", headers: auth, body: JSON.stringify(request) })));
    expect(app.devices.registered()).toHaveLength(1);
    const res = await app.fetch(new Request("http://x/control/v1/gates", { method: "POST", body: JSON.stringify({ type: "ui_request", sessionId: "sess_secret", projectId: "proj_secret", frameId: "frame_secret" }) }));
    expect(res.status).toBe(200);
    expect(fake.sends[0]!.deviceToken.tokenHash).toBe(app.devices.get(registration.deviceId)!.tokenHash);
    expect(app.devices.registered()).toHaveLength(0);
  });
});


describe("G005 blocker regressions", () => {
  const controlRead = { authorization: "Bearer control_token_sessions_read" };
  const allSkipReasons = ["stale_ttl", "max_restored_sessions", "crash_loop_cap", "project_missing", "cwd_unavailable", "config_changed", "credential_unavailable", "manual_stop", "route_invalid"] as const;
  const persisted = (id: string, n = 1) => ({ id, projectId: "proj_7f3a", projectHash: "hash_project_7f3a", routeHash: `hash_route_${id}`, routeId: `route_${id}`, gjcSessionId: `gjc_${id}`, status: "ready" as const, scopes: defaultScopes(), startedAt: "2026-06-14T15:00:00Z", lastActiveAt: `2026-06-14T16:1${n}:00Z`, lastSeq: n, restoreEligibility: "eligible" as const });
  async function registryWith(entries: any[]) {
    const dir = await mkdtemp(join(tmpdir(), "gjc-g005-state-"));
    await Promise.all(entries.map(entry => writeFile(join(dir, `${entry.id}.json`), JSON.stringify(entry))));
    const config = defaultConfig();
    return { config, metrics: new Metrics(), dir };
  }

  test("GET /control/v1/sessions requires sessions:read bearer scope", async () => {
    const app = createApp(defaultConfig());
    expect((await app.fetch(new Request("http://x/control/v1/sessions"))).status).toBe(401);
    const denied = await app.fetch(new Request("http://x/control/v1/sessions", { headers: { authorization: "Bearer control_token_devices_write" } }));
    expect(denied.status).toBe(403);
    expect((await body(denied)).error.code).toBe("scope_denied");
    const ok = await app.fetch(new Request("http://x/control/v1/sessions", { headers: controlRead }));
    expect(ok.status).toBe(200);
  });

  test("restore skipped DTO matches v2 fixture shape", async () => {
    const registry = new SessionRegistry(defaultConfig(), new Metrics());
    const fixture = (await import("../../fixtures/v2/control/restore-skipped.json")).default as any;
    registry.recordRestoreSkip({ ...fixture.session, projectHash: "hash_project_7f3a", routeHash: fixture.session.routeIdHash }, "max_restored_sessions", 4);
    expect(registry.restoreSkipped[0]).toEqual({ session: fixture.session, restore: fixture.restore });
  });

  test("restore.attempt counts persisted records and matches metrics fixture", async () => {
    const entries = [persisted("sess_a", 1), persisted("sess_b", 2), persisted("sess_c", 3), persisted("sess_d", 4)];
    const { config, metrics, dir } = await registryWith(entries);
    const registry = new SessionRegistry(config, metrics, dir);
    await restoreWithinBudget({ config, registry, now: new Date("2026-06-14T16:20:00Z") });
    const fixture = (await import("../../fixtures/v2/persistence/restore-metrics.json")).default as any;
    const expected = fixture.events.find((event: any) => event.event === "restore.attempt").count;
    expect(metrics.counters["restore.attempt"]).toBe(expected);
  });

  test("closed restore skip reason set is exhaustive", () => {
    const registry = new SessionRegistry(defaultConfig(), new Metrics());
    for (const reason of allSkipReasons) registry.recordRestoreSkip(persisted(`sess_${reason}`), reason, 9);
    expect(registry.restoreSkipped.map(item => item.restore.reason)).toEqual([...allSkipReasons]);
  });

  test("crash-loop backoff is only emitted for crash-loop cap and hashes raw ids", async () => {
    const crash = { ...persisted("sess_raw_crash", 1), crashRestarts: ["2026-06-14T16:11:00Z", "2026-06-14T16:12:00Z", "2026-06-14T16:13:00Z"] };
    const ok = persisted("sess_raw_ok", 2);
    const { config, metrics, dir } = await registryWith([crash, ok]);
    const registry = new SessionRegistry(config, metrics, dir);
    const lines: string[] = [];
    const original = console.log;
    console.log = (line?: unknown) => { lines.push(String(line)); };
    try { await restoreWithinBudget({ config, registry, now: new Date("2026-06-14T16:20:00Z") }); }
    finally { console.log = original; }
    const backoffLogs = lines.filter(line => line.includes('"event":"crash_loop.backoff"'));
    expect(backoffLogs).toHaveLength(1);
    expect(backoffLogs[0]).not.toContain("sess_raw_crash");
    expect(backoffLogs[0]).toContain("hash_session_");
    expect(lines.join("\n")).not.toContain('"sessionHash":"sess_raw_ok"');
    expect(metrics.counters["crash_loop.cap"]).toBe(1);
    expect(metrics.counters["crash_loop.backoff"]).toBe(1);
  });

  test("idle and degraded gauges are mutually exclusive", async () => {
    const app = createApp(defaultConfig());
    const started = await app.registry.start("proj_7f3a", { bridgeUrl: "http://127.0.0.1:1" });
    const sessionHash = Object.keys(app.metrics.gauges["supervisor_session_idle"])[0] ?? "";
    app.registry.markIdle(started.session.id);
    const idleHash = Object.keys(app.metrics.gauges["supervisor_session_idle"])[0]!;
    expect(app.metrics.gauges["supervisor_session_degraded"][idleHash]).toBeUndefined();
    app.registry.markDegraded(started.session.id);
    expect(app.metrics.gauges["supervisor_session_idle"][idleHash]).toBeUndefined();
    expect(app.metrics.gauges["supervisor_session_degraded"][idleHash]).toBeTruthy();
    app.registry.markIdle(started.session.id);
    expect(app.metrics.gauges["supervisor_session_degraded"][idleHash]).toBeUndefined();
    expect(app.metrics.gauges["supervisor_session_idle"][idleHash]).toBeTruthy();
    expect(sessionHash).toBeString();
  });
});

describe("G009 in-process supervisor E2E", () => {
  test("covers one project/session identity through start, proxy events, gate push, devices, mismatch, and respawn", async () => {
    const fakePush = new FakePushProvider("apns");
    const app = createApp(defaultConfig(), { providers: { apns: fakePush } });
    let eventPath = "";
    const bridge = fakeBridge((req) => {
      const url = new URL(req.url);
      if (url.pathname === "/v1/handshake") {
        return Response.json({ ok: true, authorization: req.headers.get("authorization") });
      }
      if (url.pathname === eventPath) {
        const gateFrame = {
          type: "permission_request",
          payload: {
            projectId: "proj_7f3a",
            sessionId: "sess_7f3a",
            sessionAlias: "session_alias_primary",
            frameId: "frame_7f3a",
          },
        };
        return new Response([
          "data: boot\n\n",
          "data: ready\n\n",
          `data: ${JSON.stringify(gateFrame)}\n\n`,
        ].join(""), {
          headers: { "content-type": "text/event-stream" },
        });
      }
      return Response.json({ ok: true });
    });

    const initialProjects = await body(await app.fetch(new Request("http://x/control/v1/projects")));
    expect(initialProjects.projects.some((project: any) => project.status === "running")).toBe(true);
    expect(initialProjects.projects.some((project: any) => project.status === "stopped")).toBe(true);

    const start = await body(await app.fetch(new Request("http://x/control/v1/projects/proj_7f3a/sessions", { method: "POST" })));
    app.registry.route(start.route.routeId)!.bridgeUrl = bridge.url;
    app.registry.route(start.route.routeId)!.bridgeToken = "bridge_secret";
    eventPath = `/v1/sessions/${start.session.gjcSessionId}/events`;
    expect(start.session.id).toBe("sess_7f3a");
    expect(start.session.projectId).toBe("proj_7f3a");
    expect(start.session.status).toBe("ready");
    expect(start.route.sessionId).toBe(start.session.id);
    expect(start.route.projectId).toBe(start.session.projectId);
    expect(start.route.routePath).toBe(`/s/${start.route.routeId}`);

    const runningProjects = await body(await app.fetch(new Request("http://x/control/v1/projects")));
    const runningProject = runningProjects.projects.find((project: any) => project.id === start.session.projectId);
    expect(runningProject.status).toBe("running");

    const handshake = await app.fetch(new Request(`http://x/s/${start.route.routeId}/v1/handshake`, {
      headers: { authorization: `Bearer ${start.route.scopedToken}` },
    }));
    expect(handshake.status).toBe(200);
    expect((await body(handshake)).authorization).toBe("Bearer bridge_secret");

    const events = await app.fetch(new Request(`http://x/s/${start.route.routeId}${eventPath}`, {
      headers: { authorization: `Bearer ${start.route.scopedToken}` },
    }));
    expect(events.status).toBe(200);
    expect(events.headers.get("content-type")).toContain("text/event-stream");
    const eventStream = await events.text();
    expect(eventStream).toContain("data: boot");
    expect(eventStream).toContain("data: ready");
    const proxiedGateFrame = eventStream
      .split("\n\n")
      .map((chunk) => chunk.trim())
      .filter((chunk) => chunk.startsWith("data: {") || chunk.startsWith("data: ["))
      .map((chunk) => JSON.parse(chunk.slice("data: ".length)))
      .find((frame) => detectGateNotification(frame, {
        projectId: start.session.projectId,
        sessionId: start.session.id,
        sessionAlias: "session_alias_primary",
      }));
    const gate = detectGateNotification(proxiedGateFrame, {
      projectId: start.session.projectId,
      sessionId: start.session.id,
      sessionAlias: "session_alias_primary",
    });
    expect(gate?.projectId).toBe(start.session.projectId);
    expect(gate?.sessionId).toBe(start.session.id);
    expect(gate?.frameId).toBe("frame_7f3a");

    const device = await body(await app.fetch(new Request("http://x/control/v1/devices", {
      method: "POST",
      headers: { authorization: "Bearer control_token_devices_write" },
      body: JSON.stringify({
        installId: "install_secret",
        platform: "ios",
        environment: "sandbox",
        pushToken: "device_secret",
        appVersion: "1",
        capabilities: ["push"],
      }),
    })));
    expect(device.deviceId).toStartWith("dev_");

    const gateRes = await app.fetch(new Request("http://x/control/v1/gates", {
      method: "POST",
      body: JSON.stringify(proxiedGateFrame),
    }));
    expect(gateRes.status).toBe(200);
    expect(fakePush.sends).toHaveLength(1);
    expect(fakePush.sends[0]!.payload.publicPayload.safeTitleAlias).toBe("input_needed");
    expect(fakePush.sends[0]!.payload.publicPayload.safeBodyAlias).toBe("open_app_to_continue");
    assertAliasOnlyPayload(fakePush.sends[0]!.payload.publicPayload, [
      "install_secret",
      "device_secret",
      "plaintext prompt",
      start.route.scopedToken,
      "bridge_secret",
    ]);

    const laterDevice = await body(await app.fetch(new Request("http://x/control/v1/devices", {
      method: "POST",
      headers: { authorization: "Bearer control_token_devices_write" },
      body: JSON.stringify({
        installId: "install_secret_2",
        platform: "android",
        environment: "production",
        pushToken: "device_secret_2",
        appVersion: "1",
        capabilities: ["push"],
      }),
    })));
    expect(laterDevice.deviceId).toStartWith("dev_");

    const wrongToken = mintScopedToken({
      routeId: "wrong_route",
      sessionId: start.session.id,
      projectId: start.session.projectId,
      scopes: defaultScopes(),
      issuedAt: new Date().toISOString(),
    }, "wrong_route_token");
    const bridgeContactsBeforeMismatch = bridge.seen.length;
    const mismatch = await app.fetch(new Request(`http://x/s/${start.route.routeId}/v1/handshake`, {
      headers: { authorization: `Bearer ${wrongToken}` },
    }));
    expect(mismatch.status).toBe(403);
    expect((await body(mismatch)).error.code).toBe("route_token_mismatch");
    expect(bridge.seen).toHaveLength(bridgeContactsBeforeMismatch);

    const oldRouteId = start.route.routeId;
    const oldToken = start.route.scopedToken;
    const respawn = await body(await app.fetch(new Request(`http://x/control/v1/sessions/${start.session.id}:respawn`, { method: "POST" })));
    expect(respawn.route.revocation).toBe("respawn_required");
    expect(respawn.route.routeId).not.toBe(oldRouteId);
    const bridgeContactsBeforeRevoked = bridge.seen.length;
    const revoked = await app.fetch(new Request(`http://x/s/${oldRouteId}/v1/handshake`, {
      headers: { authorization: `Bearer ${oldToken}` },
    }));
    expect(revoked.status).toBe(401);
    expect(bridge.seen).toHaveLength(bridgeContactsBeforeRevoked);
  });
});
