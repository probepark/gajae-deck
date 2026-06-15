# ADR 0001: Use a Bun/TypeScript supervisor for v2 remote control

- Status: Accepted
- Date: 2026-06-14
- Scope: gajae-deck v2 "Claude 리모컨" supervisor/control-plane runtime

## Decision

Use a dedicated Bun/TypeScript `supervisor/` package for the v2 local control-plane service.

The supervisor owns project/session orchestration, bridge subprocess lifecycle, `/control/v1/*` APIs, `/s/{routeId}` reverse proxying, device and push-provider integration, gate notification handling, structured redacted observability, boot/restore policy, and health/ready/metrics endpoints.

The runtime is pinned in `supervisor/package.json` and CI. The service contract is frozen in `docs/v2/supervisor-service-contract.md`; implementation phases must follow that contract instead of inventing substitute lifecycle, state, logging, credential, or CI policy.

## Drivers

1. **Security and public-cloud minimization**: APNs/FCM payloads must be safe by construction. Public payloads use aliases and opaque IDs only. Route mismatches and confused-deputy cases fail closed before bridge contact.
2. **Brownfield velocity with protocol stability**: Reuse bridge v2, existing GJC bridge env names, agent-wire concepts, KMP client patterns, Caddy same-origin routing, and Tailscale mesh without redesigning the transport.
3. **Single-operator operability**: Install, launchd lifecycle, pinned runtime, health, readiness, logs, state layout, provider credential rotation, restore budgets, and CI commands must be deterministic and testable.
4. **Redaction-by-construction**: Artifacts, logs, push payloads, UI errors, and fixtures must not expose cwd, repo/customer names, paths, prompts, command args, display names, bearer/control/scoped/bridge/owner/device tokens, APNs tokens, or FCM tokens.
5. **Focused v2 scope**: The solution must not add multi-tenancy, self-hosted relay infrastructure, protocol redesign, or dynamic Caddy admin routing.

## Alternatives

### Option A: Bun/TypeScript supervisor

Pros:

- Closest to GJC bridge implementation stack, bridge environment names, subprocess semantics, and agent-wire concepts.
- Strong fit for spawning `gjc`, managing bridge child processes, implementing a small HTTP reverse proxy, and handling streaming routes.
- Fast iteration with explicit DTO schemas, contract fixtures, fake bridge tests, fake push tests, and redaction corpus checks.

Cons:

- Adds a JS/Bun toolchain to the repository.
- Requires explicit long-running service hardening, launchd packaging, pinned runtime, state layout, and observability policy.

Decision: chosen for v2.

### Option B: Kotlin/JVM supervisor

Pros:

- Aligns with existing KMP/Ktor/Kotlin serialization conventions and could share DTO patterns with the companion app.
- Mature server, observability, TLS, and APNs/FCM library ecosystem.

Cons:

- Heavier daemon runtime and packaging for a thin local process manager.
- Further from GJC TypeScript bridge internals and Node/Bun subprocess behavior.
- Slower path to fake bridge/fake push harnesses that match the bridge process model.

Decision: keep as fallback only if Bun service packaging becomes a blocker or policy rejects a JS runtime.

### Option C: Swift, Go, or another native daemon

Pros:

- Strong native launchd friendliness and small deployment surface.

Cons:

- Introduces another toolchain and duplicates schemas.
- Low reuse with the existing GJC bridge and KMP client work.
- Raises implementation cost without solving the contract risks better than Bun/TS.

Decision: not recommended for v2.

## Why

Bun/TypeScript is the smallest practical runtime that keeps the supervisor near the GJC bridge implementation model while still allowing a dedicated local daemon package. It supports direct subprocess management, explicit HTTP/proxy behavior, fast test harnesses, and schema-driven contract tests.

The accepted v2 design depends on fail-closed route validation, scoped token handling, bridge respawn-based revocation, fake bridge/fake push integration tests, and structured redacted logs. Those behaviors are closest to the existing bridge stack in a Bun/TS service and can be frozen through package-level fixtures before broader CMP refactoring begins.

The added toolchain risk is real, so the decision requires a pinned Bun runtime, deterministic CI commands, launchd semantics, state layout, provider credential rotation policy, health/ready/metrics contracts, and redaction tests as first-class deliverables.

## Consequences

- A new `supervisor/` package will be added in implementation phases.
- `supervisor/package.json` must pin Bun exactly and CI must verify the active Bun version.
- The supervisor module layout must include `config.ts`, `server.ts`, `projects.ts`, `sessions.ts`, `tokens.ts`, `proxy.ts`, `devices.ts`, `push.ts`, `gates.ts`, `observability.ts`, `boot.ts`, and `health.ts`.
- launchd lifecycle behavior, exit-code classes, state directory layout, provider credential storage/rotation, structured redacted JSON-line logs, and health/ready/metrics are contractually fixed before implementation.
- Bridge protocol v2 remains intact. The supervisor starts bridge processes with the approved environment contract and proxies same-origin routes under `/s/{routeId}`.
- Route-token mismatch must fail closed before any fake or real bridge contact.
- Public push payloads must use aliases/opaque IDs only; APNs/FCM payload bodies must not contain secrets, project display names, prompts, paths, or command args.
- Persistence remains bounded by explicit restore budgets, stale TTLs, crash-loop backoff/caps, degraded/idle visibility, and skip-reason metrics.
- CI must include pinned Bun install, `bun install --frozen-lockfile`, `bun test`, fixture schema tests, fake bridge tests, fake push tests, route streaming tests, and redaction corpus checks.
- Implementers must not replace this with Kotlin/JVM or a native daemon unless this ADR is superseded.

## Follow-ups

1. Create the `supervisor/` Bun/TS package with the frozen module layout and exact Bun pin.
2. Implement config loading, state lock, XDG-based state directory layout, structured redacted logs, and health/ready/metrics endpoints first.
3. Add contract fixtures for control API responses, public push payloads, provider health, device registry, restore skips, and structured errors.
4. Add fake bridge tests for spawn env, stop, respawn revocation, route mismatch no-contact rejection, streaming, and health.
5. Add fake APNs/FCM tests for payload allowlist, TTL/expiration, collapse IDs, priority, provider credential errors, and invalid-token pruning.
6. Add redaction corpus coverage for route/control/scoped/bridge tokens, owner token, APNs/FCM token, device token, deep-link token query, cwd/repo/customer/path examples, prompt snippets, command args, and project display names.
7. Document the operator runbook for launchd install, start/stop/restart, credential rotation, cert renewal, revocation by respawn, restore budgets, and degraded provider handling.
8. Supersede this ADR only if Bun packaging becomes a blocker or policy rejects a JS runtime; the fallback must preserve the same public contracts and redaction requirements.
