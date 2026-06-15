# Supervisor v2 Operator Runbook

## Start, stop, restart (launchd)

Install a static plist from `ops/web-proxy/com.gajae-deck.supervisor.plist.template` after replacing placeholders with host-local paths and account names. Do not generate Caddy config dynamically.

```sh
launchctl bootstrap gui/$UID ~/Library/LaunchAgents/com.gajae-deck.supervisor.plist
launchctl kickstart -k gui/$UID/com.gajae-deck.supervisor
launchctl print gui/$UID/com.gajae-deck.supervisor
launchctl bootout gui/$UID/com.gajae-deck.supervisor
```

Restart is `kickstart -k`; stop is `bootout`; start after a stop is `bootstrap`.

## Revocation and respawn

Route tokens are bearer credentials. Revocation is by respawn, not in-place mutation:

1. Identify the opaque supervisor session id through `GET /control/v1/sessions`.
2. Call `POST /control/v1/sessions/{sessionId}:respawn` with a control token carrying `sessions:start`/operator scope.
3. Discard the old route path and scoped token. Old route tokens are revoked and old route ids return unauthorized or not found.
4. Confirm `session.respawn` and a fresh route hash in audit logs. Logs must contain only `projectHash`, `sessionHash`, and `routeHash`.

## Credential rotation

Provider credentials are referenced by environment aliases (`APNS_CREDENTIAL_REF`, `FCM_CREDENTIAL_REF`) rather than logged values.

1. Install the new secret in the platform secret store.
2. Update the launchd environment reference in the static plist or its sourced environment file.
3. Restart the supervisor with `launchctl kickstart -k`.
4. Check `/metrics` provider credential health gauges and `push.sent`/`push.failed` counters.
5. Rotate route exposure by respawning affected sessions. Never write raw device, owner, control, scoped, bridge, APNs, or FCM tokens to state, logs, or tickets.

## Certificate renewal

TLS termination remains in the static web proxy layer. Renew certificates with the host package/cert automation, then reload the proxy service. The supervisor does not write dynamic Caddy configuration and does not persist private keys.

## Restore budget

At supervisor boot, persisted session metadata is restored only when it fits `docs/v2/persistence-budget.md`:

- `maxRestoredSessions`: 3 most recent active sessions.
- `staleSessionTtl`: 12h.
- `crashLoopWindow`: 10m.
- `crashLoopMaxRestarts`: 3.
- `crashLoopBackoff`: 5s, 30s, 2m, capped at 10m plus jitter up to 20%.
- `idleSoftLimit`: 2h.

Skip `stale_ttl`, `max_restored_sessions`, `crash_loop_cap`, `project_missing`, `cwd_unavailable`, `config_changed`, `credential_unavailable`, `manual_stop`, `route_invalid`.

Use `GET /control/v1/sessions` for restored/skipped/degraded/idle status and `/metrics` for `supervisor_restore_*`, `supervisor_crash_loop_*`, `supervisor_session_degraded`, and `supervisor_session_idle` families. State files contain hashes and aliases only; they must not contain raw paths, repo names, prompts, command arguments, or tokens.
