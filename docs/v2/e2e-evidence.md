# E2E Evidence: Wasm Static Origin + Supervisor Proxy

This note records the external web/static path that complements the in-process G009 supervisor E2E. The G009 automated test runs entirely in Bun with `createApp()` and `fakeBridge()`; browser/device/provider execution is intentionally outside this lane.

## Path diagram

```text
operator browser / CMP wasm
  |
  |  static assets from :shared:wasmJsBrowserDistribution
  v
Caddy static origin
  |
  |-- /control/*  ----------------> supervisor HTTP API
  |-- /s/{routeId}/*  ------------> supervisor route proxy
                                      |
                                      |-- Authorization rewritten to GJC_BRIDGE_TOKEN
                                      v
                                    gjc bridge v2 / fakeBridge in G009
```

## Procedure reference

1. Build the CMP web distribution with `./gradlew :shared:wasmJsBrowserDistribution` when running the external web lane.
2. Serve the generated wasm/browser output as the Caddy static root.
3. Use `ops/web-proxy/Caddyfile.supervisor.example` for the supervisor-backed origin shape:
   - static CMP assets served by Caddy;
   - `/control/*` reverse-proxied to supervisor;
   - `/s/*` reverse-proxied to supervisor;
   - no Caddy admin API dependency.
4. Start supervisor with the same control host/port expected by the Caddyfile.
5. Load the web UI and exercise pairing/settings/session screens against supervisor.

## Existing artifacts

- `docs/e2e/g009-web-ko-settings.png` — prior Korean web settings load evidence.
- `docs/e2e/g009-web-ko-pairing.png` — prior Korean web pairing load evidence.
- `docs/e2e/web-e2e.png` — prior web E2E screenshot.
- `ops/web-proxy/Caddyfile.supervisor.example` — static origin plus supervisor proxy example.

## G009 automated coverage boundary

`supervisor/test/supervisor.test.ts` now includes `G009 in-process supervisor E2E`, which proves the supervisor side of the chain without relying on Caddy, a real browser, real APNs/FCM, or a physical device:

1. `GET /control/v1/projects` returns running and stopped projects.
2. `POST /control/v1/projects/{projectId}/sessions` starts a scoped route with a route token.
3. `/s/{routeId}/v1/handshake` and `/events` reach `fakeBridge()` through the proxy, including authorization rewrite and SSE streaming.
4. Gate frame detection produces an alias-only payload and `FakePushProvider` send.
5. `POST /control/v1/devices` registers protected device metadata under `devices:write`.
6. Route-token mismatch returns fail-closed without contacting the bridge.
7. Respawn revokes the old route/token and old-route access fails without bridge contact.

## Control-first 리모컨 UI/UX + issues 3·4 (Phases 0-6)

Control-first Korean remote-control UI evidence for the negotiated `?demo=1` web session and issues 3·4 gate/palette work.

### Screenshots

- `docs/e2e/g007-web-ko-session-demo.png` — semantic transcript plus actionable `ActionRequestPanel` for `Permission: bash` with Allow/Deny/Always controls and grouped command palette; Korean renders without tofu.
- `docs/e2e/g007-web-ko-palette-full.png` — full grouped command palette with `PROMPT`, `SESSION`, `MESSAGE_READ`, and `MODEL` groups visible.
- `docs/e2e/g007-web-ko-gate-resolved.png` — after clicking Allow, the gate panel is resolved/removed and `CONTROL` + `EXPORT` palette groups are visible.

### Web demo lane

```sh
./gradlew :shared:wasmJsBrowserDistribution
serve shared/build/dist/wasmJs/productionExecutable
open 'http://localhost:4099/?demo=1'
```

The `?demo=1` flag renders `DemoApp` with a negotiated `FakeBridge` demo session.

### Verification

- Kotlin desktop tests: `./gradlew :shared:desktopTest` — 195 tests, 0 failures.
- Kotlin target compilation: `./gradlew :shared:compileKotlinDesktop :shared:compileKotlinIosSimulatorArm64 :androidApp:compileAndroidMain :shared:compileKotlinWasmJs` — 4 targets compile (`compileKotlinDesktop`, `compileKotlinIosSimulatorArm64`, `compileAndroidMain`, `compileKotlinWasmJs`).
- Supervisor checks: `bun test` — 40 pass; `tsc --noEmit` clean (`tsc --noEmit clean`).
- Desktop live-bridge E2E is env-gated and requires a live gjc bridge:

```sh
GJC_BRIDGE_E2E=1 GJC_BRIDGE_TOKEN=... GJC_BRIDGE_BASE=... ./gradlew :shared:desktopTest --tests '*LiveBridgeE2eTest'
```

### Delivered phases and deferred scope

- P0: `readLine` + single `ResumeCursor` + `GateVocabulary` fail-closed + reconnect-storm handling.
- P1: bounded `TranscriptState` ring + semantic reducer + `PendingGateIndex`.
- P2: sealed gate adapters + `ActionRequestPanel` with redaction-by-construction.
- P3: five gate response paths: correlation match, Outbox exclusive, in-flight exact-once, fail-closed actuator, and session-scoped always-allow.
- P4: `CommandRegistry` 38 total / 12 exposed commands, grouped scope-gated palette, and schema form.
- P5: app + supervisor canonical capability/scope alignment and negotiation fixtures.
- P6: evidence docs record the control-first UI/UX screenshots, verification lanes, and deferred items.
- Deferred/non-goals: real-device iOS/Android E2E, native TLS pinning/cert-rotation, 25 non-exposed command dedicated forms, upstream PR merge, and per-story QA artifact.

