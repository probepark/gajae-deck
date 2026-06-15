# v2 Supervisor Service Contract

Status: Phase 0 frozen contract. Implementation belongs to later phases.

This document freezes the Bun/TypeScript supervisor contract for gajae-deck v2 "Claude 리모컨". It is authoritative for the future `supervisor/` package and must not be replaced by executor-local policy.

## Runtime and package ownership

- Runtime: Bun is pinned exactly as `packageManager: "bun@1.3.14"` in `supervisor/package.json`.
- CI must verify the active `bun --version` equals `1.3.14` before install or tests run.
- Runtime upgrades require updating the pin and passing the service fixture tests, fake bridge tests, fake push tests, and redaction corpus.
- Package root: `supervisor/` is owned by the v2 remote-control supervisor feature.
- The supervisor is a local single-operator control-plane service. It does not introduce multi-tenancy, a relay, protocol redesign, or dynamic Caddy admin routing.

## Required module layout

The package must keep policy in explicit modules with these responsibilities:

| Module | Contract |
| --- | --- |
| `config.ts` | Project registry, public base URL, control-token hash reference, provider credential references, and persistence-budget defaults. |
| `server.ts` | HTTP listener for `/control/v1/*`, `/s/{routeId}/*`, `health`, `ready`, and `metrics`. |
| `projects.ts` | Project cwd validation, non-secret display metadata ingestion, and hashed public summaries. Raw cwd, repo names, customer names, paths, and display names never leave this module unredacted. |
| `sessions.ts` | Bridge spawn/stop/respawn, route binding, health probes, crash-loop backoff, and recent-session persistence. |
| `tokens.ts` | Control/scoped token parsing, timing-safe comparison, route-claim validation, owner-token/idempotency mapping, and scope matrix. |
| `proxy.ts` | Streaming reverse proxy, `/s/{routeId}` path rewrite, header sanitization, bridge auth rewrite, and no-contact route rejection. |
| `devices.ts` | Protected device registry, device hash derivation, APNs/FCM token storage reference, and invalid-token pruning. |
| `push.ts` | APNs/FCM adapters, public payload alias renderer, retry policy, fake provider, TTL, collapse-id hash, and provider error mapping. |
| `gates.ts` | User-input-waiting frame detector, notification eligibility, gate collapse policy, and gate resolution events. |
| `observability.ts` | JSON-line redacted logs, metrics, audit events, and redaction corpus enforcement. |
| `boot.ts` | launchd lifecycle integration, state lock, restore budget enforcement, stale/crash-loop skip handling, and startup sequencing. |
| `health.ts` | Health, readiness, and metrics response contracts. |

## launchd lifecycle semantics

`launchd` controls only the supervisor daemon. Bridge children are owned by the supervisor and are managed through session APIs and restore policy.

### Start

- Loads and starts the supervisor daemon.
- Acquires the state lock before accepting requests.
- Validates config, state directory permissions, provider credential references, listener bind, and persistence-budget defaults.
- Restores only sessions allowed by the persistence budget.
- A restored session receives fresh in-memory route validation. Secrets are never serialized into launchd plist arguments or labels.

### Stop

- Rejects new control requests and new session starts.
- Terminates child bridge processes gracefully, then forcefully after the supervisor-defined grace period.
- Flushes state and final redacted audit events.
- Invalidates in-memory route bindings. Persisted state must not contain bearer/control/scoped/bridge/owner/device tokens in plaintext.

### Restart

- Performs `stop` then `start` semantics.
- Preserves allowed persistent state, not in-memory route bindings.
- Revalidates recent sessions under the persistence budget.
- Credential rotation uses `update credentials, then supervisor restart`.

### Exit codes

The service must use stable exit-code classes so launchd/operator tooling can distinguish failures:

| Code | Meaning |
| --- | --- |
| `0` | Clean stop. |
| `10` | Configuration error: malformed config, missing required project registry, invalid public base URL, or invalid persistence-budget values. |
| `11` | Credential reference error: provider credential reference missing, unreadable, invalid, expired, or inconsistent with configured environment. |
| `12` | State error: state directory cannot be created, opened, locked, or has unsafe permissions. |
| `13` | Port bind/listener error. |
| `14` | Child crash threshold exceeded during boot restore or required startup probe. |
| `20` | Unexpected supervisor fault after startup. |

Invalid provider credentials degrade health and use exit code `11` only when they prevent required startup validation. There is no silent fallback to an alternate provider.

## State directory layout

Base directory:

- If `XDG_STATE_HOME` is set: `$XDG_STATE_HOME/gajae-deck/supervisor-v2/`
- Otherwise: platform default user state root plus `gajae-deck/supervisor-v2/`

No filename, directory name, JSON key, SQLite table value intended for artifact review, or log line may contain raw route/control/scoped/bridge/owner/device tokens, APNs/FCM tokens, cwd, repo names, customer names, prompt text, command args, or project display names.

Required layout:

```text
supervisor-v2/
  lock
  config.snapshot.json
  projects/
    {projectHash}.json
  sessions/
    {sessionHash}.json
  routes/
    {routeHash}.json
  devices/
    {deviceHash}.json
  providers/
    apns.status.json
    fcm.status.json
  restore/
    attempts.jsonl
    skips.jsonl
  logs/
    supervisor.jsonl
  metrics/
    counters.json
```

- `{projectHash}`, `{sessionHash}`, `{routeHash}`, and `{deviceHash}` are deterministic non-secret hashes or aliases, not reversible identifiers.
- Route records may contain token hashes and route metadata but never route tokens.
- Session records may contain bridge process metadata, restore budget metadata, and hashed IDs but never bridge tokens or command payloads.
- Device records may contain protected credential references and token hashes but never public-cloud device tokens in plaintext unless the platform-protected store contract explicitly requires local encrypted storage; artifact exports must still redact them.

## Provider credentials

- APNs and FCM credentials are referenced by `config.ts`; raw credential bodies are not embedded in config snapshots, logs, fixtures, push payloads, or screenshots.
- Credentials are stored in platform-protected storage when available. File-backed development credentials must use restricted permissions and must be referenced by alias or hash in state.
- Rotation procedure: install/update the provider credential, restart the supervisor, verify `/control/v1/health` provider status, then prune invalid device tokens from failed delivery results.
- Provider credential errors are observable as degraded provider health and structured error codes. They do not fall back to another credential, environment, bundle, or Firebase project.
- Fake APNs/FCM providers are required for CI and fixture tests; public payloads must match the push payload allowlist.

## Structured redacted logs

Logs are JSON Lines. Every line is one JSON object matching this schema shape:

```json
{
  "ts": "2026-06-14T00:00:00.000Z",
  "level": "info",
  "event": "session.start",
  "requestId": "req_opaque",
  "projectHash": "h_project",
  "sessionHash": "h_session",
  "routeHash": "h_route",
  "deviceHash": "h_device",
  "code": "started",
  "retryable": false,
  "durationMs": 12
}
```

Required fields:

- `ts`: RFC 3339/ISO timestamp.
- `level`: `debug`, `info`, `warn`, or `error`.
- `event`: stable event name.

Optional fields are limited to:

- `requestId`
- `projectHash`
- `sessionHash`
- `routeHash`
- `deviceHash`
- `code`
- `retryable`
- `durationMs`

Additional fields require a contract update and redaction-corpus coverage. Plaintext cwd, repo/customer names, filesystem paths, route/control/scoped/bridge tokens, owner tokens, device tokens, APNs/FCM tokens, prompt text, command names or args, APNs/FCM payload bodies, project display names, and deep-link token queries are prohibited.

Required event families:

- `supervisor.start`, `supervisor.stop`, `supervisor.ready`, `supervisor.degraded`
- `project.list`, `project.reject`
- `session.start`, `session.stop`, `session.respawn`, `session.health`, `session.idle`, `session.degraded`
- `route.reject`, `route.proxy`, `route.stream`
- `device.register`, `device.unregister`, `device.prune`
- `push.sent`, `push.failed`, `push.provider_degraded`
- `gate.detected`, `gate.collapsed`, `gate.resolved`
- `restore.attempt`, `restore.success`, `restore.skipped`
- `crash_loop.backoff`

## Health, readiness, and metrics

All endpoints are under the control origin and return no secrets.

### `GET /control/v1/health`

Returns HTTP `200` when the supervisor is running, including degraded provider/session state. The body uses `schemaVersion: 1`, `requestId`, `serverTime`, and status sections:

```json
{
  "schemaVersion": 1,
  "requestId": "req_health",
  "serverTime": "2026-06-14T00:00:00.000Z",
  "status": "degraded",
  "supervisor": { "status": "ok", "versionAlias": "supervisor_version_alias" },
  "config": { "status": "ok" },
  "provider": { "status": "degraded", "code": "provider_unhealthy" },
  "store": { "status": "ok" },
  "bridges": { "ready": 1, "idle": 1, "degraded": 0, "stopped": 1 }
}
```

Errors use `{ "error": { "code", "message", "retryable", "details"? } }` and must not include secrets.

### `GET /control/v1/ready`

Returns HTTP `200` only when config is valid, state lock is held, and the listener is accepting requests. Returns non-`200` with a structured error if startup, lock, config, or listener prerequisites fail. Provider degradation alone does not make the service unready unless configured as a hard startup requirement.

### `GET /control/v1/metrics`

Returns counters/gauges with hashed IDs only. Metrics must include at least:

- active sessions, degraded sessions, idle sessions
- restore attempts, restore successes, restore skipped by reason
- route rejects by code, including `route_token_mismatch`
- bridge crash-loop backoff and cap counts
- push sent/failed by provider and hashed collapse key
- provider credential health gauge

Metrics must never expose raw token values, paths, prompts, command args, display names, provider payload bodies, or device tokens.

## CI commands and gates

Supervisor CI is run from `supervisor/` and must include:

```sh
bun install --frozen-lockfile
bun test
```

Additional required checks:

- Bun version check for `1.3.14` before install/test.
- Fixture schema tests for control API, public push payloads, health/ready/metrics, structured errors, and restore/device/provider cases.
- Fake bridge tests for spawn env, route mismatch, no-contact fail-closed proxy rejection, streaming, health, stop, and respawn revocation.
- Fake push tests for APNs/FCM payload shape, TTL/expiration, collapse IDs, priority, invalid-token pruning, and provider credential errors.
- Redaction corpus tests for route/control/scoped/bridge tokens, owner token, APNs/FCM token, device token, deep-link token query, cwd/repo/customer/path examples, prompt snippets, command args, and project display names.
- CI cache key includes Bun version and lockfile hash.

## Service test specification

Implementation must provide service tests that prove:

1. Boot accepts a valid temp state dir, acquires a lock, emits only allowed JSON-line log fields, and exposes ready.
2. Boot rejects malformed config with exit code `10` and a structured redacted error.
3. Credential validation reports provider degradation or exit code `11` according to startup policy.
4. Unsafe state directory or lock failure exits `12` without leaking filesystem paths into artifact logs.
5. Port bind failure exits `13`.
6. Restore enforces max restored sessions, stale TTL, crash-loop window, crash-loop cap, backoff, and skip reasons.
7. Session start spawns `gjc` bridge with required env values and project cwd, but tests assert only aliases/hashes in logs and artifacts.
8. Session stop terminates the child bridge and invalidates the route.
9. Session respawn kills the bridge, mints a new session token, preserves conversation intent through gjc commands, returns a new route token, and marks prior route revocation as `respawn_required`.
10. `/s/{routeId}` with mismatched scoped token fails closed with `route_token_mismatch` and fake bridge records no contact.
11. Proxy streaming preserves SSE/NDJSON behavior and sanitizes headers.
12. Health reports supervisor, config, provider, store, and bridge aggregate counts without secrets.
13. Ready is `200` only when config, state lock, and listener are ready.
14. Metrics expose counters/gauges with hashed identifiers only.
15. Fake APNs/FCM providers receive only public allowlisted payload aliases and opaque IDs.
16. Negative redaction corpus fails if prohibited plaintext appears in payloads, logs, UI-intended errors, screenshots, or fixture artifacts.
