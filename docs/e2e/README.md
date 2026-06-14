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

### Finding (RESOLVED in G007/G009)
Korean glyphs previously rendered as tofu boxes on the **Web and Desktop** (skiko) targets because
the default skiko font lacks CJK coverage. Fixed by bundling a subset Noto Sans KR variable font
(OFL) as a Compose resource and binding it as the default Material3 typography (G007), then wiring it
through the Koin composition root (G009).

## Web (G009 integration, ko locale) — PASS
The Koin-backed composition root (`App` → `GajaeDeckTheme` → `AppNavHost`) rendered in headless
Chromium with a `ko-KR` locale against the compiled wasm distribution:
- Pairings screen renders Korean with real glyphs — `가재덱`, `페어링`, `아직 페어링이 없습니다.`,
  `호스트` / `포트` / `Bearer 토큰`, `연결`, `설정` (no tofu). Screenshot: `g009-web-ko-pairing.png`.
- Navigation to Settings (`설정`) shows the theme selector (`시스템`/`라이트`/`다크`) and the web
  lower-assurance notice (web `SecureStore` is `BROWSER_LOCAL_STORAGE`). Screenshot:
  `g009-web-ko-settings.png`.

Desktop shares the identical skiko renderer, bundled font, and Compose code path, so the Web render
is representative of the Desktop surface; Desktop logic is covered by the green `:shared:desktopTest`
suite (90 tests).

## Pending (require devices/simulators)
- iOS real E2E (Xcode simulator) + native Keychain SecureStore + Darwin TLS pinning runtime test.
- Android real E2E (emulator) + EncryptedSharedPreferences SecureStore runtime test.
- Web browser-trusted-TLS pairing against a real cert (Caddy/Tailscale) + camera-less manual entry.
