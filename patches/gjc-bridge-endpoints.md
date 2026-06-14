# Patch: gjc bridge opt-in endpoint enablement

External dependency. The CMP client requires the gjc bridge session endpoints to be reachable.
The installed gjc runtime is hardcoded fail-closed: `runBridgeMode` calls `createBridgeFetchHandler`
without an `endpointMatrix`, so every session endpoint returns `403 endpoint_disabled`.

This patch adds an **opt-in** `GJC_BRIDGE_ENDPOINTS` env (default stays fail-closed, backward compatible).
It must be applied to the gjc install (`@gajae-code/coding-agent`) or contributed upstream / vendored in a fork.

File: `src/modes/bridge/bridge-mode.ts`

1) Add a parser (next to `parseBridgeScopes`):

```ts
function parseBridgeEndpoints(value: string | undefined): Partial<BridgeEndpointMatrix> {
	if (!value?.trim()) return {};
	const keys: (keyof BridgeEndpointMatrix)[] = [
		"events", "commands", "control", "uiResponses", "hostToolResults", "hostUriResults",
	];
	const allowed = new Set<string>(keys);
	const matrix: Partial<BridgeEndpointMatrix> = {};
	for (const raw of value.split(",")) {
		const entry = raw.trim();
		if (!entry) continue;
		if (entry === "all") { for (const k of keys) matrix[k] = true; continue; }
		if (!allowed.has(entry)) throw new Error(`Invalid GJC_BRIDGE_ENDPOINTS entry: ${entry}`);
		matrix[entry as keyof BridgeEndpointMatrix] = true;
	}
	return matrix;
}
```

2) In `runBridgeMode`, after `commandScopes`:

```ts
const endpointMatrix = parseBridgeEndpoints(Bun.env.GJC_BRIDGE_ENDPOINTS);
```

3) Thread it into the handler options (the `createBridgeFetchHandler({...})` call):

```ts
		commandScopes,
		endpointMatrix,
		unattendedControlPlane,
```

## Run the bridge (dev)
```sh
export GJC_BRIDGE_TOKEN=<random>
export GJC_BRIDGE_HOST=127.0.0.1 GJC_BRIDGE_PORT=4077 GJC_BRIDGE_ENDPOINTS=all
export GJC_BRIDGE_SCOPES="prompt,control,bash,export,session,model,message:read,host_tools,host_uri,admin"
export GJC_BRIDGE_TLS_CERT=cert.pem GJC_BRIDGE_TLS_KEY=key.pem
gjc --mode bridge
```

## Web (browser) topology
The bridge sends no CORS headers. Serve the web app and proxy `/healthz` + `/v1/*` from one origin
(Caddy `tls internal` for dev, real/Tailscale cert for prod) — see `docs/bridge/protocol-v2.md`.
