# AC1-AC9 Traceability

Source acceptance: `.gjc/plans/ralplan/2026-06-14-0252-7802/pending-approval.md` and `.gjc/specs/deep-interview-cmp-remote-control-v2.md`.

Status values:
- `covered`: covered by in-process supervisor/CMP contract implementation or tests in this repository.
- `deferred-external`: requires physical device, killed-app OS push delivery, real APNs/FCM provider, reboot/launchd, or cross-target UI execution tracked outside G009.

| AC | Requirement summary | Evidence | Status |
|---|---|---|---|
| AC1 | Projects list shows running/stopped and reports project status. | `supervisor/projects.ts:listProjects`; `supervisor/server.ts:createApp` `GET /control/v1/projects`; CMP model/client references. | covered |
| AC2 | Sessions can be started and exposed through control API without leaking route credentials. | `POST /control/v1/projects/{projectId}/sessions`; `supervisor/sessions.ts:SessionRegistry`; fixture-backed session start tests. | covered |
| AC3 | Route proxy forwards only scoped, authorized route calls. | `/s/{routeId}/v1/*`; `supervisor/proxy.ts:proxyRoute`; `docs/v2/control-api.md` route endpoints and scopes. | covered |
| AC4 | Permission/workflow gate creates alias-only APNs/FCM-safe push payload. | `supervisor/gates.ts:detectGateNotification`; `supervisor/push.ts:renderAliasOnlyPush`, `payloadFromGate`, `assertAliasOnlyPayload`; `supervisor/providers/index.ts:FakePushProvider`; `supervisor/test/supervisor.test.ts` G009 in-process E2E verifies proxied event stream gate frame -> push send. | covered |
| AC5a | In-process resume contract: route proxy reconnect/event streaming, `lastSeq` field contract, and CMP resume client/handler implementation. | `docs/v2/control-api.md` documents `Session.lastSeq` and route event stream; `supervisor/proxy.ts:proxyRoute` preserves SSE streaming; `shared/src/commonMain/...` BridgeSessionClient/DeepLinkResumeHandler implementation; `shared/src/commonTest/...` common tests; `supervisor/test/supervisor.test.ts` covers route proxy streaming and reconnect-safe route identity. | covered |
| AC5b | Physical app resume after killed-app push, gate approval, and completion on Android/iOS. | Requires real OS push delivery, physical/simulator app lifecycle, and APNs/FCM credentials; tracked by G006 external device lane. | deferred-external (G006) |
| AC6 | Persistence budget restores eligible sessions and reports skip reasons. | `supervisor/boot.ts:restoreWithinBudget`; `supervisor/sessions.ts:SessionRegistry.persisted/restoreSkipped`; `supervisor/observability.ts:Metrics`; boot and G005 blocker regression tests cover max restored sessions, stale TTL, crash-loop cap, skip reasons, idle/degraded gauges; `docs/v2/persistence-budget.md`. | covered |
| AC7 | Multiple simultaneous clients control safely with scoped claim/disconnect/idempotency behavior. | `supervisor/proxy.ts:ROUTE_ENDPOINTS`, `requiredScopeFor`, `proxyRoute`; `docs/v2/control-api.md`; regression tests for scoped route claims. | covered |
| AC8 | Token-route mismatch is fail-closed and bridge is not contacted. | `supervisor/proxy.ts:proxyRoute`; `supervisor/tokens.ts:validateRouteClaim`, `revokeScopedToken`; `route_token_mismatch` contract in `docs/v2/control-api.md`; G009 E2E verifies no bridge contact on mismatch and revoked old route after respawn. | covered |
| AC9 | Secrets are absent from push/log/UI surfaces; aliases and hashes only. | `supervisor/observability.ts:logJson`, `hashId`, `tokenHash`; `supervisor/push.ts:assertAliasOnlyPayload`; `supervisor/devices.ts:DeviceRegistry`; alias-only push negative corpus, redactor corpus, log schema, fake provider, and device protected metadata tests. | covered |
| G006/G008 external push lane | Real killed-app notification receipt, real provider credentials, and physical Android/iOS delivery. | Real APNs/FCM provider wiring: `supervisor/providers/index.ts:ApnsProvider`, `FcmProvider`; Android/iOS app targets. Not run in G009; requires device farm or physical device credentials. | deferred-external |
| External web/static origin lane | Wasm distribution behind Caddy static origin and `/control/*`, `/s/*` proxy to supervisor. | `ops/web-proxy/Caddyfile.example`, `ops/web-proxy/bridge.env.example`, `shared/webpack.config.d/devServer.js`, `docs/e2e/README.md`. | covered |

## G007 Control-first 리모컨 UI/UX + issues 3·4 Phases 0-6 traceability

| Phase | Acceptance item | Verification / evidence | Status |
| --- | --- | --- | --- |
| P0 | `readLine` handling, single `ResumeCursor`, `GateVocabulary` fail-closed parsing, and reconnect-storm recovery. | `ResumeCursorTest`; `GateVocabularyTest`; `ReconnectStormTest`; Kotlin `:shared:desktopTest` 195 tests / 0 failures. | covered |
| P1 | Bounded transcript ring, semantic transcript reducer, and pending gate indexing. | `TranscriptStateTest`; `PendingGateIndexTest`; Kotlin `:shared:desktopTest` 195 tests / 0 failures. | covered |
| P2 | Sealed gate adapters and redaction-by-construction `ActionRequestPanel` for permission/workflow gates. | `GateAdaptersTest`; `ActionRequestPanelLogicTest`; `docs/e2e/g007-web-ko-session-demo.png` shows actionable `Permission: bash` Allow/Deny/Always controls with Korean text rendered. | covered |
| P3 | Five gate response paths: correlation match, Outbox exclusive routing, in-flight exact-once behavior, fail-closed actuator behavior, and session-scoped always-allow. | `GateResponseTest`; `Phase3InFlightExactOnceTest`; `docs/e2e/g007-web-ko-gate-resolved.png` shows resolved/removed gate after Allow; Kotlin `:shared:desktopTest` 195 tests / 0 failures. | covered |
| P4 | `CommandRegistry` 37 total / 12 exposed commands, grouped scope-gated command palette, and schema form behavior. | `CommandRegistryTest`; `Phase4PaletteAdversarialTest`; `docs/e2e/g007-web-ko-palette-full.png` shows `PROMPT`, `SESSION`, `MESSAGE_READ`, `MODEL`; `docs/e2e/g007-web-ko-gate-resolved.png` shows `CONTROL` and `EXPORT`. | covered |
| P5 | App and supervisor canonical capability/scope alignment with negotiated fixture coverage. | `NegotiationFixturesTest`; `HandshakeRequestTest`; supervisor handshake test; supervisor `bun test` 40 pass and `tsc --noEmit` clean. | covered |
| P6 | Evidence and traceability docs record the control-first UI/UX screenshots, build/serve/demo lane, test matrix, live-bridge gate, and deferred scope. | `docs/v2/e2e-evidence.md`; `docs/v2/ac-traceability.md`; screenshots under `docs/e2e/g007-*.png`; web lane `./gradlew :shared:wasmJsBrowserDistribution`, `serve shared/build/dist/wasmJs/productionExecutable`, `open http://localhost:4099/?demo=1`. | covered |
| GUI surface | Korean control-first 리모컨 web UI renders semantic transcript, grouped palette, and resolved gate state. | `docs/e2e/g007-web-ko-session-demo.png`; `docs/e2e/g007-web-ko-palette-full.png`; `docs/e2e/g007-web-ko-gate-resolved.png`. | covered |
| Desktop live-bridge path | Desktop live bridge exercises the real bridge only when a live gjc bridge is available. | `GJC_BRIDGE_E2E=1 GJC_BRIDGE_TOKEN=... GJC_BRIDGE_BASE=... ./gradlew :shared:desktopTest --tests '*LiveBridgeE2eTest'`; `LiveBridgeE2eTest`. | deferred-external |
| G007 deferred/non-goals | Real-device iOS/Android E2E, native TLS pinning/cert-rotation, 25 non-exposed command dedicated forms, upstream PR merge, and per-story QA artifact. | Explicitly outside Phase 0-6 evidence lane; tracked as non-goal/deferred scope rather than covered acceptance. | deferred-external |

