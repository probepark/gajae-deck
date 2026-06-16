# Repository Guidelines

## Project Overview

**gajae-deck** is a Compose Multiplatform companion / remote-control app for the
**gajae-code (gjc)** coding agent's **Bridge protocol v2**. It pairs a Kotlin
Multiplatform client (`shared/`, targets: Android, Desktop/JVM, iOS, wasmJs
browser) with a Bun/TypeScript **supervisor** control plane (`supervisor/`).

The supervisor is a single-operator local daemon that orchestrates `gjc --mode
bridge` subprocesses, exposes a control API (`/control/v1/*`), reverse-proxies
streaming routes (`/s/{routeId}/v1/*`) with fail-closed scoped-token validation,
and emits alias-only APNs/FCM push notifications when the agent hits a
user-input *gate*. The Korean-localized UI ships a bundled Noto Sans KR font.

## Architecture & Data Flow

Two planes, two toolchains:

- **Control plane** — KMP app → `KtorControlPlaneClient` → supervisor
  `/control/v1/*` (control bearer token, coarse scopes). Lists projects, starts/
  stops/respawns sessions, registers push devices. Supervisor mints an opaque
  `routeId` + scoped route token bound to a session.
- **Data plane** — KMP app streams through `/s/{routeId}/v1/*` (proxy.ts). The
  proxy validates `routeId` + token + session/project binding + required scope
  **before** contacting the bridge (fail-closed `route_token_mismatch` /
  `scope_denied`, `bridgeContacted:false`), rewrites `Authorization` to the
  bridge token, and streams SSE/NDJSON unbuffered. First events request prepends
  gjc JSONL conversation history.
- **Push path** — bridge gate frames → `gates.ts` → alias-only payload
  (`push.ts`) → APNs/FCM (`providers/index.ts`) → registered devices.
- **Boot** — launchd runs only the supervisor; `boot.ts` restores ≤3 recent
  sessions subject to stale-TTL / crash-loop / cwd / project checks.
- **Web topology** — the gjc bridge sends **no CORS headers**, so the browser
  target requires a Caddy same-origin reverse proxy (`ops/web-proxy/`).

**Redaction invariant (enforced everywhere):** logs, metrics, push payloads,
persisted state, navigation routes, and UI errors contain only deterministic
hashes/aliases — never cwd, repo names, paths, prompts, command args, or any
token. New code MUST preserve this.

## Key Directories

| Path | Purpose |
|------|---------|
| `shared/src/commonMain/.../` | Nearly all client logic + Compose UI (`bridge/`, `control/`, `ui/`, `navigation/`, `auth/`, `settings/`, `observability/`, `notifications/`, `di/`, `theme/`) |
| `shared/src/{android,desktop,ios,wasmJs}Main/` | Platform `actual`s only (Koin bindings, Ktor engine, SecureStore) |
| `shared/src/commonTest/`, `shared/src/desktopTest/` | KMP tests (kotlin.test) |
| `androidApp/src/main/` | Thin Android host (Application + Activity + manifest) |
| `supervisor/` | Bun/TS control-plane service (`server.ts` entry) |
| `fixtures/` | Golden JSON fixtures shared by both test stacks (`v2/`, `bridge-v2/`) |
| `docs/` | ADRs (`adr/`), frozen v2 contracts (`v2/`), bridge protocol (`bridge/`), E2E evidence (`e2e/`) |
| `ops/web-proxy/` | Caddy same-origin proxy + launchd templates |
| `patches/` | Opt-in patch to the external gjc bridge dependency |

## Development Commands

**Gradle (run from repo root):**
```bash
./gradlew :androidApp:assembleDebug        # Android APK
./gradlew :androidApp:installDebug         # install on device/emulator
./gradlew :shared:run                      # Desktop app (mainClass io.devnogari.gajaedeck.MainKt)
./gradlew :shared:wasmJsBrowserDevelopmentRun   # web dev server (WASM_DEV_EXPOSE=1 to expose on LAN)
./gradlew :shared:packageDistributionForCurrentOS  # Desktop Dmg/Msi/Deb
./gradlew check                            # build + lint + tests
./gradlew :androidApp:lintDebug            # Android lint
```

**Supervisor (run from `supervisor/`):**
```bash
bun install
bun test                                   # bun:test runner
bun run typecheck                          # tsc --noEmit
bun run server.ts                          # start supervisor (default 127.0.0.1:8787)
```

## Code Conventions & Common Patterns

**Kotlin (`shared/`)**
- Root package `io.devnogari.gajaedeck`, feature sub-packages. Files PascalCase
  per primary type; platform actuals use `.android`/`.desktop`/`.ios`/`.wasmJs`
  suffix (e.g. `Koin.android.kt`, `HttpClientFactory.ios.kt`).
- `expect`/`actual` for `platformModule()`, `bridgeEngineFactory()`, SecureStore
  and Settings backends, device registrar/push provider.
- **DI:** Koin. Shared modules in `di/AppModules.kt`; `di/Koin.kt` declares
  `expect fun platformModule()` + idempotent `initGajaeDeckKoinOnce()`. UI pulls
  singletons via `koinInject()`; parameterized clients via factory +
  `parametersOf()`.
- **State:** `SessionController` is the ViewModel-equivalent, exposes
  `StateFlow<SessionUiState>`; screens use `produceState` / `LaunchedEffect` /
  `collectAsState`. `ObservableAppSettings` uses write-through `StateFlow`s.
- **Errors:** suspend functions return `Result<T>`; typed
  `BridgeException`/`ControlPlaneException` with error-code enums + `fromWire()`;
  `ErrorHandler` maps codes to fixed, secret-free user copy.
- **Networking:** Ktor with per-platform engines (OkHttp Android/Desktop, Darwin
  iOS, Js wasmJs), one shared `HttpClient` per connector. kotlinx.serialization
  `@Serializable` DTOs with `@SerialName`; lenient `Json`.
- **Navigation:** type-safe `navigation-compose` `@Serializable` routes
  (`Routes.kt`); routes carry only opaque ids, never tokens.
- **commonMain boundary rule:** no `android.*`, `androidx.security.*`, or
  `koin.android.*` in `commonMain` (documented in `shared/build.gradle.kts`).
- Logging via Kermit through the redacting `AppLogger` facade.

**TypeScript (`supervisor/`)**
- ES modules (`type: module`), strict TS (`noUncheckedIndexedAccess`,
  `exactOptionalPropertyTypes`). Bun + `node:crypto`/`node:fs`.
- One responsibility per module (layout frozen by ADR/service contract).
  Stateful registries are `class`es (`SessionRegistry`, `DeviceRegistry`,
  `Metrics`); everything else is exported pure functions + `interface`/`type`
  DTOs and string-literal union types for states/codes.
- `async/await` with localized `try/catch` mapping failures to structured
  `errorResponse` codes. Timing-safe token comparison (`timingSafeTokenEqual`).
  Deterministic `FIXED_TIME`/`HOST_HASH` constants for fixture-stable tests.

## Important Files

- `shared/src/commonMain/kotlin/io/devnogari/gajaedeck/App.kt` — shared root composable.
- Entry points: `androidApp/.../MainActivity.kt`, `shared/src/desktopMain/.../main.kt`, `shared/src/wasmJsMain/.../main.kt`, `shared/src/iosMain/.../MainViewController.kt`.
- `.../di/AppModules.kt`, `.../di/Koin.kt` — DI wiring.
- `.../navigation/AppNavHost.kt`, `.../navigation/Routes.kt` — navigation graph.
- `.../ui/SessionController.kt` — core orchestrator (health→handshake→event stream).
- `.../bridge/Protocol.kt` — frozen protocol contract (`BRIDGE_PROTOCOL_VERSION = 2`).
- `.../auth/Redactor.kt`, `.../auth/SecureStore.kt` — security primitives.
- `supervisor/server.ts` (entry), `proxy.ts`, `sessions.ts`, `boot.ts`, `tokens.ts`, `gates.ts`, `push.ts`, `observability.ts`.
- Build: `gradle/libs.versions.toml` (version catalog), `shared/build.gradle.kts`, `androidApp/build.gradle.kts`, `supervisor/package.json`, `supervisor/tsconfig.json`.
- Contracts: `docs/adr/0001-v2-supervisor.md`, `docs/v2/supervisor-service-contract.md`, `docs/v2/control-api.md`, `docs/bridge/protocol-v2.md`.

## Runtime/Tooling Preferences

- **JDK:** ≥21 (Desktop target is `JVM_21`; Android/`androidApp` use `JVM_11`). Toolchains resolved via foojay-resolver-convention 1.0.0.
- **Gradle:** wrapper pinned to **9.5.1** — use `./gradlew`. Build cache enabled; daemons capped at `-Xmx3g`.
- **Bun:** pinned **1.3.14** (`packageManager` in `supervisor/package.json`). Use **Bun, not Node/npm/yarn** for the supervisor (`bun.lock`). The wasmJs target uses yarn internally via the Kotlin plugin (`kotlin-js-store/`) — do not manage it manually.
- **Versions:** AGP 9.0.1, Kotlin 2.3.21, Compose Multiplatform 1.11.0, Ktor 3.4.3, coroutines 1.10.2, kotlinx-serialization 1.9.0, Koin 4.2.1. SDKs: compile/target 36, min 26.

## Testing & QA

- **KMP:** `kotlin.test` across all targets; async via `kotlinx-coroutines-test`
  (`runTest`). One `<Subject>Test` class per file mirroring production packages;
  adversarial/phase suites (`PhaseNAdversarialTest`). Doubles:
  `ktor-client-mock` (`MockEngine`), `multiplatform-settings-test` (`MapSettings`),
  `koin-test`, `InMemorySecureStore`, `Fake*` connectors.
  ```bash
  ./gradlew :shared:allTests                          # all targets
  ./gradlew :shared:desktopTest                       # JVM only
  ./gradlew :shared:desktopTest --tests '*CommandRegistryTest*'
  ```
- **Live bridge E2E** is gated behind env vars so default builds stay deterministic:
  ```bash
  GJC_BRIDGE_E2E=1 GJC_BRIDGE_TOKEN=<token> GJC_BRIDGE_BASE=https://127.0.0.1:4077 \
    ./gradlew :shared:desktopTest --tests '*LiveBridgeE2eTest*'
  ```
- **Supervisor:** `bun test` (single suite `supervisor/test/supervisor.test.ts`,
  `describe`/`test`/`expect` from `bun:test`); uses ephemeral `Bun.serve` fake
  bridges + shared `fixtures/`. Test names encode acceptance-criteria/G IDs.
- **No CI or coverage tooling** is configured in-repo. QA is acceptance-criteria
  driven; evidence tracked in `docs/v2/ac-traceability.md` and `docs/e2e/`.
  Both stacks assert against the golden `fixtures/` to enforce wire-shape
  conformance with the frozen `docs/v2` and `docs/bridge` contracts.
