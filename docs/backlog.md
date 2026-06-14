# Backlog (post-milestone follow-ups)

Milestone accepted: verified CMP bridge-client core — Desktop + Web real E2E, all 4 targets
compile, 49 tests green, live `gjc --mode bridge` integration proven.

Deferred (require devices/simulators or are polish):

## Real E2E on remaining platforms
- iOS: create `iosApp` Xcode project, boot a simulator, build/install/run, drive pair→connect→prompt→event→gate→complete against the bridge.
- Android: emulator run of `:androidApp` driving the same flow.

## Native platform actuals (currently Desktop-only / in-memory)
- iOS Keychain-backed `SecureStore` + Darwin `NSURLSession` TLS pinning (challenge capture, host-scoped, rotation).
- Android `EncryptedSharedPreferences` (Keystore) `SecureStore` + OkHttp `CertificatePinner` wiring.
- Web: `localStorage`/`IndexedDB` storage with session-only mode + lower-assurance warning.

## UI / UX
- Bundle a CJK-capable font as a Compose resource and set it as the default `FontFamily`
  (Korean renders as tofu boxes on Web/Desktop with the skiko default font; Android/iOS use system fonts).
- Dedicated answer UIs for `permission_request` / `workflow_gate` / `ui_request` / `host_tool_call` / `host_uri_request`.
- Per-command forms for the full catalog (currently a prompt field + a few palette buttons).
- Live GUI screenshots per native platform.

## Process / hardening
- ultragoal formal quality-gate checkpoints (architect + executor QA) per story.
- Expanded stress matrix: reconnect storm, background resume, cert rotation, large history, renderer virtualization perf.
- Upstream the gjc bridge opt-in `GJC_BRIDGE_ENDPOINTS` patch (see `patches/gjc-bridge-endpoints.md`) instead of patching `node_modules`.
- Migrate `ByteReadChannel.readUTF8Line` (deprecated) to `readLine`.
