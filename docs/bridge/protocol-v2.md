# gjc Bridge Protocol v2 — Authoritative Contract (Frozen)

> Source of truth: `@gajae-code/coding-agent/src/modes/bridge/*` and `.../modes/shared/agent-wire/*`
> at the version installed in this environment (BRIDGE_PROTOCOL_VERSION = 2).
> This freeze is the contract the CMP client implements and tests against (fixtures in `fixtures/bridge-v2/`).

## Transport
- HTTPS only. One bridge process serves exactly one live `AgentSession`.
- Default bind `127.0.0.1:4077` (`GJC_BRIDGE_HOST`/`GJC_BRIDGE_PORT`). TLS via `GJC_BRIDGE_TLS_CERT`/`GJC_BRIDGE_TLS_KEY`.
- Auth: `Authorization: Bearer <GJC_BRIDGE_TOKEN>` on all authenticated routes.
- Owner correlation: `X-GJC-Bridge-Owner-Token` (optional; participates in idempotency/controller correlation).
- Writes (commands, UI responses) carry `Idempotency-Key`; identical key+body+route returns the cached response, key reuse with different owner → `403 {status:rejected, code:not_controller}`, different body → `409 {error:idempotency_conflict}`.
- **No CORS**: the bridge sends no `Access-Control-*` headers; cross-origin `OPTIONS` to authenticated routes returns `401`. Browsers MUST use a same-origin reverse proxy (e.g. Caddy) that serves the web app and proxies `/healthz` + `/v1/*`.
- **Fail-closed by default**: session endpoints are disabled unless explicitly enabled. In this environment a local opt-in patch threads `GJC_BRIDGE_ENDPOINTS` into the endpoint matrix (see `docs/phase0/phase0-gate-result.md`). Clients MUST treat `403 {error:endpoint_disabled, endpoint:<name>}` as "unavailable".

## Endpoints
| Method | Path | Auth | Endpoint-matrix key |
|--------|------|------|---------------------|
| GET | `/healthz` | no | always on |
| GET | `/v1/help` | no | always on (reports matrix) |
| POST | `/v1/handshake` | yes | always on |
| GET | `/v1/sessions/{id}/events?last_seq=<n>` | yes | `events` |
| POST | `/v1/sessions/{id}/commands` | yes | `commands` |
| POST | `/v1/sessions/{id}/control:claim` | yes | `control` |
| POST | `/v1/sessions/{id}/control:disconnect` | yes | `control` |
| POST | `/v1/sessions/{id}/ui-responses/{correlation_id}` | yes | `uiResponses` |
| POST | `/v1/sessions/{id}/host-tool-results/{correlation_id}` | yes | `hostToolResults` |
| POST | `/v1/sessions/{id}/host-uri-results/{correlation_id}` | yes | `hostUriResults` |

Endpoint matrix keys: `events`, `commands`, `control`, `uiResponses`, `hostToolResults`, `hostUriResults`.

## Handshake
Request (`BridgeHandshakeRequest`):
```json
{
  "protocol_version_range": { "min": 1, "max": 2 },
  "capabilities": ["events","prompt","permission","workflow_gate","host_tools","host_uri","ui.declarative","elicitation"],
  "requested_scopes": ["message:read","prompt","control"],
  "last_seq": 0,
  "unattended": null
}
```
Accepted response (`BridgeHandshakeAccepted`): `status:"accepted"`, `protocol_version:2`, `session_id`, `accepted_capabilities[]`, `accepted_scopes[]`, `unsupported[]`, `endpoints{events,commands,uiResponses,claimControl,disconnectControl,hostToolResults,hostUriResults}` (disabled ones are `""`), `frame_types[]`, optional `accepted_unattended`/`unattended_active`.
Rejected: `status:"rejected"`, `reason:"incompatible_version"|"unauthorized"|"invalid_request"`, `message`.

Server capabilities advertised when `events` enabled: `events, prompt, permission, elicitation, ui.declarative, host_tools, host_uri, workflow_gate`.

## Frame types (server → client, over events)
`ready`, `event`, `response`, `ui_request`, `permission_request`, `host_tool_call`, `host_uri_request`, `reset`, `workflow_gate`, `error`.

Stream framing: content-type-adaptive; client parser MUST tolerate SSE (`text/event-stream`) and NDJSON. Each frame carries `protocol_version`, `session_id`, `seq`, `frame_id`. Reconnect with `?last_seq=<n>`; `reset` frame with `replay_window_exceeded` clears the local timeline (recover via snapshot).

## Command catalog (`type` → required scope)
`POST /v1/sessions/{id}/commands` body: `{ "type": <RpcCommandType>, ...params }`. Authoritative registry (37 commands):

| type | scope |
|------|-------|
| prompt | prompt |
| steer | prompt |
| follow_up | prompt |
| abort | prompt |
| abort_and_prompt | prompt |
| new_session | session |
| get_state | message:read |
| set_todos | control |
| set_host_tools | host_tools |
| set_host_uri_schemes | host_uri |
| set_model | model |
| cycle_model | model |
| get_available_models | model |
| set_thinking_level | model |
| cycle_thinking_level | model |
| set_steering_mode | control |
| set_follow_up_mode | control |
| set_interrupt_mode | control |
| compact | control |
| set_auto_compaction | control |
| set_auto_retry | control |
| abort_retry | control |
| bash | bash |
| abort_bash | bash |
| get_session_stats | message:read |
| export_html | export |
| switch_session | session |
| branch | session |
| get_branch_messages | session |
| get_last_assistant_text | message:read |
| set_session_name | session |
| handoff | admin |
| get_messages | message:read |
| get_login_providers | admin |
| login | admin |
| negotiate_unattended | control |
| workflow_gate_response | prompt |

Scopes (`BridgeCommandScope`): `prompt, control, bash, export, session, model, message:read, host_tools, host_uri, admin`. Mandatory floor: `prompt`. Server scopes set via `GJC_BRIDGE_SCOPES` (comma list). A command whose scope is not granted → `403 {error:scope_denied, scope:<scope>}`.

> Note: the deep-interview spec referenced "~19 commands"; the actual frozen catalog is **37** command types. The CMP `CommandCatalog` MUST cover the full registry above (UI may group/prioritize, but typed payloads exist for all).

## Command response shape
`{ "type":"response", "command":<type>, "success":boolean, "data"?:object, "error"?:object }` (see `fixtures/bridge-v2/command-get_session_stats.response.json`).

## Typed error codes (client `BridgeTransport`)
`endpoint_disabled`, `unauthorized`, `scope_denied`, `invalid_request`, `invalid_json`, `invalid_command`, `idempotency_conflict`, `not_controller`, `commands_unavailable`, plus client-side: `NetworkBlocked, AuthBlocked, ProtocolBlocked, TlsBlocked, CorsBlocked, FirstUseCertificateDecisionRequired, StreamBuffered, EndpointDisabled, Timeout, ServerRejected, StaleResponse`.
