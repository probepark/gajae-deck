# v2 Public push payload contract

Phase 0 freezes the public APNs/FCM/deep-link contract for gate notifications. Public-cloud payloads are **alias-only by construction**: providers receive opaque ids and allowlisted aliases, never user, project, route, token, prompt, command, or path material.

## Allowlist constants

Only these aliases are valid in provider payloads:

```text
safeTitleAlias = input_needed
safeBodyAlias = open_app_to_continue
```

Renderers may map the aliases to fixed localized strings inside the app. The alias values are constants; they must not be derived from a project, session, command, prompt, cwd, path, token, device, branch, remote, or display name.

## Gate notification schema

Internal gate event, not sent to APNs/FCM as-is:

```json
{
  "schemaVersion": 1,
  "type": "user_input_waiting",
  "sessionId": "ses_alias_7f3a",
  "projectId": "prj_alias_31bd",
  "routeId": "route_alias_91ce",
  "gateKind": "permission_request",
  "frameSeq": 42,
  "frameId": "frame_alias_b1c0",
  "createdAt": "2026-06-14T16:20:00Z"
}
```

Public push payload shape:

```json
{
  "schemaVersion": 1,
  "notificationId": "ntf_opaque_01JZ0J4M9K8W7Y6X5V4T3S2R1Q",
  "safeTitleAlias": "input_needed",
  "safeBodyAlias": "open_app_to_continue",
  "category": "gate.input_needed",
  "collapseIdHash": "col_h_4f6e2a91c0bd",
  "deepLinkOpaqueId": "dl_opaque_01JZ0J4N0P9Q8R7S6T5V4W3X2Y",
  "createdAtBucket": "2026-06-14T16:15:00Z"
}
```

## Allowed public fields

Public push fixtures are provider request envelopes plus provider payloads. Every field below is explicitly allowlisted; no other public APNs/FCM/deep-link field may be emitted.

Common fixture envelope fields:

- `schemaVersion`
- `requestId`
- `serverTime`
- `provider`

Common public payload fields:

- `schemaVersion`
- `notificationId`
- `safeTitleAlias`
- `safeBodyAlias`
- `category`
- `collapseIdHash`
- `threadIdHash`
- `deepLinkOpaqueId`
- `createdAtBucket`

APNs provider wrapper/header fields:

- `headers.apns-push-type`
- `headers.apns-priority`
- `headers.apns-topic`
- `headers.apns-collapse-id`
- `headers.apns-expiration`
- `payload.aps.alert.title-loc-key`
- `payload.aps.alert.loc-key`
- `payload.aps.category`
- `payload.aps.thread-id`
- `payload.<common public payload fields>`

FCM provider wrapper fields:

- `message.tokenAlias`
- `message.notification.title_loc_key`
- `message.notification.body_loc_key`
- `message.data.<common public payload fields>` as string values where required by FCM
- `message.android.collapse_key`
- `message.android.ttl`
- `message.android.priority`
- `message.apns.headers.apns-collapse-id`
- `message.apns.headers.apns-expiration`

Deep links expose only `deepLinkOpaqueId`. Public payloads MUST NOT include a URL-valued `deepLink`; apps resolve `deepLinkOpaqueId` locally into a route after wake.

Failure fixtures may expose only error objects shaped as `{ "code", "message", "retryable", "details"? }`, where `details` contains aliases/hashes only.

## Prohibited fields and values

Public payloads, provider requests, deep links, logs, UI errors, screenshots, and test artifacts must not contain any plaintext value from this list:

- cwd or absolute/relative filesystem paths
- repository name, customer name, project display name, session title, branch name, or git remote name
- prompt text, tool payload, command name, or command args
- route id, route token, control token, scoped token, bridge token, owner token, bearer token, APNs token, FCM token, device token, or device id
- raw `sessionId`, `projectId`, `routeId`, `frameId`, `installId`, `deviceId`, or provider credential ids
- deep-link query values carrying token material, paths, prompts, commands, or display names

The negative corpus fixture names these classes with dummy forbidden examples so CI can assert they never appear in generated provider payloads.

## Deep-link policy

Deep links include only an opaque lookup id:

```text
gajaedeck://notification/open?id=dl_opaque_01JZ0J4N0P9Q8R7S6T5V4W3X2Y
```

The app resolves the id after wake and retrieves route material from secure local storage or the control API. Deep links must not include token query parameters, route paths, session ids, project ids, cwd, prompt, command args, or display names.

## TTL, collapse, and priority policy

- TTL is 15 minutes for gate notifications.
- APNs expiration is `serverTime + 900s`; FCM `ttl` is `900s`.
- Collapse identifiers are hashes per session/gate and never raw ids.
- Supervisor sends one collapsed push per gate/frame.
- Default priority is normal. Active gate notifications may request high priority only within APNs/FCM platform rules and without changing the alias-only payload contract.
