# Persistence Budget Contract v2

This contract freezes the Phase 0 persistence budget for the v2 supervisor. Implementation belongs to a later phase; this document defines the values, restore decisions, redaction rules, fixtures, and observability that implementation must match.

## Redaction and identifiers

Persistence artifacts, logs, metrics, and UI-visible restore state must be redacted by construction. They may contain opaque aliases and hashes only. They must not contain raw cwd, repo name, customer name, filesystem path, prompt text, command arguments, project display name, bearer/control/scoped/bridge/owner tokens, APNs/FCM tokens, device tokens, or token-like deep-link query values.

Allowed identifiers in this contract are opaque examples such as `projectHash`, `sessionHash`, `routeHash`, `deviceHash`, `policyHash`, and alias values.

## Default policy values

| Field | Frozen value | Meaning |
| --- | --- | --- |
| `maxRestoredSessions` | `3` | Restore at most three recent active sessions per host after supervisor or host restart. |
| `staleSessionTtl` | `12h` | Skip sessions whose `lastActiveAt` is older than twelve hours. |
| `crashLoopWindow` | `10m` | Count bridge restarts per session inside a ten-minute rolling window. |
| `crashLoopMaxRestarts` | `3` | Skip restore after three restarts inside `crashLoopWindow`. |
| `crashLoopBackoff` | `5s → 30s → 2m → cap 10m + jitter≤20%` | Backoff schedule for restart attempts before the crash-loop cap is reached. |
| `idleSoftLimit` | `2h` | A live session with no frames for two hours is marked `idle`; this is visible but not itself a restore skip reason. |

The default policy fixture is `fixtures/v2/persistence/default-policy.json`.

## Restore selection

On supervisor or host restart, the supervisor evaluates persisted session records without exposing raw project or route details. Eligible records are ordered by most recent `lastActiveAt`; up to `maxRestoredSessions` are restored. Every non-restored record must produce exactly one primary skip reason from the closed list below.

Closed restore skip reasons:

- `stale_ttl`
- `max_restored_sessions`
- `crash_loop_cap`
- `project_missing`
- `cwd_unavailable`
- `config_changed`
- `credential_unavailable`
- `manual_stop`
- `route_invalid`

A skipped record may include redacted diagnostic details such as `lastActiveAge`, `restartCount`, `budgetRank`, or hashed ids. It must not include raw paths, display names, command arguments, prompts, or tokens.

## Degraded and idle visibility

Restore can produce visible non-secret session state without being a skip:

- `idle`: no frames observed for `idleSoftLimit` (`2h`).
- `degraded`: session remains visible, but some dependency is unhealthy or unavailable; implementation-specific details must be emitted as redacted status codes.

Degraded and idle state must be visible in session summaries, structured logs, and metrics using hashes only.

## Metrics and visibility events

The supervisor must expose counters/gauges with hashed labels only. Required event names:

- `restore.attempt`
- `restore.success`
- `restore.skipped`
- `crash_loop.backoff`
- `session.degraded`
- `session.idle`

Required metric shape:

- `supervisor_restore_attempt_total{hostHash,policyHash}`
- `supervisor_restore_success_total{hostHash,policyHash}`
- `supervisor_restore_skipped_total{hostHash,policyHash,reason}`
- `supervisor_crash_loop_backoff_total{sessionHash,projectHash}`
- `supervisor_session_degraded{sessionHash,projectHash}`
- `supervisor_session_idle{sessionHash,projectHash}`
- `supervisor_restored_sessions_current{hostHash,policyHash}`

The metrics fixture is `fixtures/v2/persistence/restore-metrics.json`.

## Fixture index

- `default-policy.json`: frozen defaults and closed skip reason list.
- `restored-recent.json`: three recent active sessions restored within budget.
- `stale-ttl-skip.json`: stale session skipped with `stale_ttl`.
- `max-restored-skip.json`: fourth eligible session skipped with `max_restored_sessions`.
- `crash-loop-skip.json`: session skipped with `crash_loop_cap` after three restarts in ten minutes.
- `degraded-idle.json`: visible `degraded` and `idle` states without secret fields.
- `restore-metrics.json`: required events and metric series for restore, skip, crash-loop, degraded, and idle visibility.
