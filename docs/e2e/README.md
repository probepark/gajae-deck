# E2E evidence

## Desktop (live bridge) — PASS
`LiveBridgeE2eTest` (guarded by `GJC_BRIDGE_E2E=1`) runs the real `KtorBridgeTransport` over OkHttp
(trust-all for the self-signed dev cert) against a running `gjc --mode bridge`:
- `GET /healthz` → ok
- `POST /v1/handshake` → accepted, `protocol_version=2`, session id
- `POST /v1/sessions/{id}/commands` `get_session_stats` → `success=true`

Run:
```sh
GJC_BRIDGE_E2E=1 GJC_BRIDGE_TOKEN=<token> GJC_BRIDGE_BASE=https://127.0.0.1:4077 \
  ./gradlew :shared:desktopTest --tests '*LiveBridgeE2eTest*'
```

## Web / Wasm (browser render) — PASS
The compiled Compose Multiplatform wasm app (`:shared:wasmJsBrowserDistribution`) was served via a
Caddy same-origin reverse proxy (`/` = app, `/healthz` + `/v1/*` → bridge) and loaded in a real
headless Chromium. The gajae-deck pairing screen renders (Host / Port / Bearer token / connect).
Screenshot: `web-e2e.png`.

### Finding (follow-up)
Korean glyphs render as tofu boxes on the **Web and Desktop** targets because the default skiko font
lacks CJK coverage (Android/iOS use system fonts and are unaffected). Bundle a CJK-capable font as a
Compose resource and set it as the default `FontFamily` to fix.

## Pending (require devices/simulators)
- iOS real E2E (Xcode simulator) + native Keychain SecureStore + Darwin TLS pinning runtime test.
- Android real E2E (emulator) + EncryptedSharedPreferences SecureStore runtime test.
- Web browser-trusted-TLS pairing against a real cert (Caddy/Tailscale) + camera-less manual entry.
