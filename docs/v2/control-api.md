# Control API v1 and wire schema

Phase 0 freezes the v2 supervisor control API and route wire contract. Implementations MUST keep these shapes stable unless a later ADR supersedes this document.

## Common envelope

Every JSON control response includes:

- `schemaVersion`: integer, currently `1`.
- `requestId`: opaque request correlation id, safe for logs and UI.
- `serverTime`: RFC 3339 timestamp from the supervisor.

List and success responses add endpoint-specific fields at the top level. Error responses add `error`:

```json
{
  "schemaVersion": 1,
  "requestId": "req_01JSAFE000000000000000000",
  "serverTime": "2026-06-14T16:20:00Z",
  "error": {
    "code": "invalid_request",
    "message": "Request body failed validation.",
    "retryable": false,
    "details": { "field": "resume" }
  }
}
```

`details` is optional and MUST contain only redacted aliases or hashes. It MUST NOT contain cwd, repo or customer names, filesystem paths, prompts, command arguments, display names, bearer tokens, bridge tokens, route tokens, owner tokens, device tokens, APNs/FCM tokens, or deep-link token material.

## Error codes

| Code | HTTP | Retryable | Meaning |
| --- | ---: | --- | --- |
| `unauthorized` | 401 | false | Missing, malformed, expired, or revoked control bearer. |
| `forbidden` | 403 | false | Authenticated control principal lacks the required permission. |
| `scope_denied` | 403 | false | Token is valid but does not include the required scope. |
| `route_token_mismatch` | 403 | false | Scoped route token does not bind to the requested `/s/{routeId}`; bridge MUST NOT be contacted. |
| `not_found` | 404 | false | Project, session, route, or device alias was not found. |
| `project_stopped` | 409 | true | Session start was requested for a stopped project. |
| `not_controller` | 409 | false | Control release/update was attempted by a client that does not own the current control claim. |
| `idempotency_conflict` | 409 | false | Idempotency key was reused with different request ownership or body hash. |
| `conflict` | 409 | false | State changed concurrently. |
| `rate_limited` | 429 | true | Supervisor applied local rate or backoff limit. |
| `bridge_start_failed` | 502 | true | Supervisor could not spawn the bridge process. |
| `bridge_unhealthy` | 502 | true | Existing bridge process failed health probing or stream setup. |
| `push_unavailable` | 503 | true | Push transport is configured off or unavailable. |
| `provider_unhealthy` | 503 | true | APNs/FCM credential or provider health is degraded. |
| `restore_skipped` | 200 | false | Restore attempt skipped by the persistence budget. |
| `internal` | 500 | true | Unexpected supervisor error after redaction. |

## Control auth

Control endpoints require:

```http
Authorization: Bearer <controlToken>
```

The bearer is a supervisor control token, not a bridge token or route token. Authorization is scope based:

- `projects:read` — list and read projects.
- `sessions:read` — list sessions and read session state.
- `sessions:start` — start project sessions.
- `sessions:stop` — stop and respawn sessions.
- `devices:write` — register, refresh, or remove push devices.
- `route_tokens:mint` — mint or refresh route-scoped tokens.

Control token revocation is performed by updating supervisor configuration and restarting the supervisor. Session/route revocation is performed by bridge respawn, which mints a new `GJC_BRIDGE_TOKEN` and route binding.

## Project model

`Project` is the redacted project summary exposed to CMP:

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | Opaque stable project id or hash. |
| `displayAlias` | string | Safe alias for UI; never a display name. |
| `cwdHash` | string | Hash only; never a path. |
| `status` | enum | `running`, `stopped`, `starting`, `stopping`, `idle`, `degraded`, `error`. |
| `lastActiveAt` | string? | RFC 3339 timestamp. |
| `lastSessionId` | string? | Opaque session id. |
| `autostart` | enum | `recent` or `never`. |
| `health` | enum | `unknown`, `ok`, `degraded`, `down`. |
| `safeSummaryAlias` | string | Alias-only summary, suitable for UI and push-adjacent logs. |

Endpoints:

- `GET /control/v1/projects` returns `{ schemaVersion, requestId, serverTime, projects: Project[] }`.
- `GET /control/v1/projects/{projectId}` returns `{ schemaVersion, requestId, serverTime, project: Project }`.

## Session model

`Session` is the supervisor-owned bridge session record:

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | Opaque supervisor session id. |
| `projectId` | string | Opaque project id. |
| `routeIdHash` | string | Hash of route id; raw route id is only in `SessionRoute`. |
| `gjcSessionId` | string? | Opaque gjc conversation/session id. |
| `status` | enum | `starting`, `ready`, `reconnecting`, `idle`, `degraded`, `stopped`, `error`. |
| `scopes` | string[] | Bridge scopes granted to the route token. |
| `startedAt` | string | RFC 3339 timestamp. |
| `lastActiveAt` | string | RFC 3339 timestamp. |
| `lastSeq` | integer | Last observed bridge frame sequence. |
| `bridgePid` | integer? | Local process id; omit or null when unavailable. |
| `restoreEligibility` | enum? | `eligible`, `ineligible`, `skipped`. |
| `skipReason` | enum? | See restore skip reasons. |
| `error` | object? | Redacted `{ code, message, retryable, details? }`. |

Restore skip reasons are `stale_ttl`, `max_restored_sessions`, `crash_loop_cap`, `project_missing`, `cwd_unavailable`, `config_changed`, `credential_unavailable`, `manual_stop`, and `route_invalid`.

Endpoints:

- `GET /control/v1/projects/{projectId}/sessions` returns `{ schemaVersion, requestId, serverTime, sessions: Session[] }`.
- `POST /control/v1/projects/{projectId}/sessions` body `{ resume: "latest"|"new"|"session_id", sessionId?, scopes, reason? }` starts a bridge with `GJC_BRIDGE_ENDPOINTS`, `GJC_BRIDGE_SCOPES`, `GJC_BRIDGE_TOKEN`, `GJC_BRIDGE_TLS_CERT`, and `GJC_BRIDGE_TLS_KEY`; returns `{ session, route }`.
- `POST /control/v1/sessions/{sessionId}:stop` stops the bridge process, invalidates the route, and returns `{ session }`.
- `POST /control/v1/sessions/{sessionId}:respawn` kills the bridge, mints a new session token, preserves project/conversation intent through gjc commands, and returns `{ session, route }`.

## Route token model: `SessionRoute`

`SessionRoute` is returned only when the client needs to connect to a bridge route:

| Field | Type | Notes |
| --- | --- | --- |
| `sessionId` | string | Bound session id. |
| `projectId` | string | Bound project id. |
| `routeId` | string | Opaque route id used in path. |
| `routePath` | string | `/s/{routeId}`. |
| `baseUrl` | string | Same-origin or configured public base URL. |
| `scopedToken` | string | Route-scoped bearer token. Fixtures use aliases only. |
| `scopes` | string[] | Bridge scopes. |
| `expiresAt` | string? | `null` for v2 indefinite route token lifetime. |
| `revocation` | string | `respawn_required`. |
| `ownerToken` | string? | Optional controller correlation token. Fixtures use aliases only. |

A scoped route token binds `routeId`, `sessionId`, `projectId`, `scopes`, and the bridge authorization rewrite target `Authorization: Bearer <GJC_BRIDGE_TOKEN>`. Raw token values MUST NOT appear in logs, UI, fixtures, or public push payloads.

## Route proxy schema

Route proxy endpoints are exposed under the route origin and require a scoped route token bound to the route, project, session, and required bridge scope:

- `GET /s/{routeId}/v1/handshake`
- `GET /s/{routeId}/v1/sessions/{gjcSessionId}/events`
- `POST /s/{routeId}/v1/sessions/{gjcSessionId}/commands`
- `POST /s/{routeId}/v1/sessions/{gjcSessionId}/ui-responses`
- `POST /s/{routeId}/v1/sessions/{gjcSessionId}/host-tool-results`
- `POST /s/{routeId}/v1/sessions/{gjcSessionId}/host-uri-results`
- `POST /s/{routeId}/v1/sessions/{gjcSessionId}/claim-control`
- `POST /s/{routeId}/v1/sessions/{gjcSessionId}/disconnect-control`

`claim-control` records the current controller using only aliases/hashes and is idempotent for the same owner and idempotency key. `disconnect-control` releases that owner; a different owner receives `409 not_controller`. Reusing an idempotency key for a different owner or body returns `409 idempotency_conflict`.

Route requests use the route token:

```http
Authorization: Bearer <scopedToken>
```

Validation order is fail-closed:

1. Parse `/s/{routeId}` from the request path.
2. Parse and authenticate the scoped route token.
3. Check token route binding, session binding, project binding, and required bridge scope.
4. If any binding fails, return `403 route_token_mismatch` or `scope_denied` without contacting the bridge.
5. Only after successful validation, rewrite upstream auth to the session bridge token and stream SSE/NDJSON without buffering.

`route_token_mismatch` MUST be observable as a supervisor `route.reject` event with hashed ids only and fake bridge contact count `0`.

## Gate notification schema

Internal gate notification records may include enough redacted control metadata for the supervisor and CMP to resolve a gate, but still no secrets or sensitive human content:

```json
{
  "schemaVersion": 1,
  "notificationId": "notif_01JSAFE000000000000000000",
  "projectId": "proj_7f3a",
  "projectAlias": "project_alias_alpha",
  "sessionId": "sess_7f3a",
  "sessionAlias": "session_alias_primary",
  "gateKind": "permission_request",
  "safeTitleAlias": "input_needed",
  "safeBodyAlias": "open_app_to_continue",
  "collapseIdHash": "hash_collapse_7f3a",
  "deepLinkOpaqueId": "dl_opaque_7f3a",
  "frameId": "frame_7f3a",
  "createdAt": "2026-06-14T16:20:00Z"
}
```

Public APNs/FCM payloads are stricter and are generated only from allowlisted aliases:

```json
{
  "schemaVersion": 1,
  "notificationId": "notif_01JSAFE000000000000000000",
  "safeTitleAlias": "input_needed",
  "safeBodyAlias": "open_app_to_continue",
  "collapseIdHash": "hash_collapse_7f3a",
  "deepLinkOpaqueId": "dl_opaque_7f3a",
  "createdAtBucket": "2026-06-14T16:15:00Z"
}
```

Public payloads MUST NOT include project display name, cwd, filesystem path, repo/customer name, prompt text, command name, command arguments, tool payload, route id, token, device token, branch, or git remote name.

## Health, readiness, metrics, and device registration

- `GET /control/v1/health` returns `schemaVersion`, `requestId`, `serverTime`, top-level `status`, and the same aggregate sections used by the supervisor service contract: `supervisor`, `config`, `provider`, `store`, and `bridges`. `provider` reports credential health without credential ids or payload bodies. `bridges` reports aggregate counts only: `ready`, `idle`, `degraded`, and `stopped`.
- `GET /control/v1/ready` returns 200 only when config, state lock, and listener are ready. The body is `{ schemaVersion, requestId, serverTime, ready, checks: { config, stateLock, listener } }`.
- `GET /control/v1/metrics` returns `{ schemaVersion, requestId, serverTime, counters, gauges, labels }` with hashed ids only. Required metric families are `restore.attempt`, `restore.success`, `restore.skipped{reason}`, `route.reject{code}`, `crash_loop.backoff`, `crash_loop.cap`, `push.sent`, `push.failed`, `provider.credential_health`, `session.start`, `session.stop`, `session.respawn`, and `gate.resolved`.
- `POST /control/v1/devices` requires `devices:write` and body `{ installId, platform: "ios"|"android", environment, pushToken, appVersion, locale?, capabilities }`; response is `{ deviceId, registeredAt }`. Device tokens are encrypted/protected when possible and redacted everywhere.
