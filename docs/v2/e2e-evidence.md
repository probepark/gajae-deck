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
