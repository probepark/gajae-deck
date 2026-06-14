// Opt-in dev-server exposure for LAN / Tailscale access.
//
// Default (env unset): webpack-dev-server keeps its normal localhost-only binding — this
// file is a no-op, so committing it does not change anyone's default `wasmJsBrowser*Run`.
//
// Set WASM_DEV_EXPOSE=1 to bind all interfaces and accept any Host header, e.g. when
// reaching the dev server over Tailscale or fronting it with a reverse proxy. Only enable
// this on a trusted network — it serves your dev build to anything that can reach the port.
if (config.devServer && process.env.WASM_DEV_EXPOSE) {
	config.devServer.host = "0.0.0.0";
	config.devServer.allowedHosts = "all";
	config.devServer.client = config.devServer.client || {};
	// Let the HMR client infer the websocket URL from window.location so hot reload works
	// when the page is served over a non-localhost host.
	config.devServer.client.webSocketURL = "auto://0.0.0.0:0/ws";
}
