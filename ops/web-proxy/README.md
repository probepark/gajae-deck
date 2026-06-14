# web-proxy — single-origin reverse proxy for the web client

The gjc bridge sends **no CORS headers**, so the browser web build cannot call it from a
different origin. Caddy fronts the static wasmJs app **and** the bridge API on one HTTPS
origin (e.g. a Tailscale MagicDNS name), which removes the CORS problem. Native clients
(Desktop/Android/iOS) talk to the bridge directly and do **not** need this.

```
browser ──HTTPS──▶ Caddy :8443 ┬─ /healthz, /v1/*  ─▶ gjc bridge https://127.0.0.1:4077
                               └─ everything else   ─▶ static wasmJs distribution (files)
```

## Setup

1. **Certs** (gitignored, in `.certs/`):
   - Bridge self-signed (see `bridge.env.example` for the `openssl` command).
   - Browser-facing cert: over Tailscale, `tailscale cert <your-magicdns-name>`; locally,
     use `tls internal` in the Caddyfile.
2. **Bridge env**: `cp bridge.env.example bridge.env`, fill in a token
   (`openssl rand -hex 32`). `GJC_BRIDGE_ENDPOINTS=all` requires the opt-in patch in
   `../../patches/gjc-bridge-endpoints.md` applied to the installed
   `@gajae-code/coding-agent` (otherwise all session endpoints stay fail-closed).
3. **Web bundle**: `./gradlew :shared:wasmJsBrowserDevelopmentExecutableDistribution`
   (Caddy serves the files live; rebuild to update — no Caddy restart needed).
4. **Caddy config**: `cp Caddyfile.example Caddyfile` and set `GAJAEDECK_PROXY_ADDR`
   (and cert paths) for your host, or export the env vars the example references.

## Run (from repo root)

```sh
set -a; . ops/web-proxy/bridge.env; set +a
gjc --mode bridge &
caddy run --config ops/web-proxy/Caddyfile
```

Then open `https://<your-origin>:8443/` and pair with Host=`<your-origin>`, Port=`8443`,
Token=`<GJC_BRIDGE_TOKEN>`.

## Never commit

`Caddyfile`, `bridge.env`, and `.certs/` are gitignored — they hold host-specific values
and secrets (bearer token, private keys). Only the `*.example` files belong in git.
