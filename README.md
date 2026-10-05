# Tariffia Panel

Android control panel for Tariffia Router, VPS/SSH and provider configuration.

Planned MVP (partially implemented):

- **VPS / SSH**: host, port, user, private key, host-key verification, Test SSH Connection.
  Implemented: profile + Keystore-encrypted key/passphrase, strict validation, explicit
  connection states (Not configured / Testing / Connected / Auth failed / Connection
  failed / host-key confirmation / host-key changed), mandatory host-key pinning with
  explicit enrollment and confirmation to forget a pin, Test Connection (JSch). Not yet:
  running commands/deploy.
- **Router**: URL, token, `/healthz`, `/v1/models`, provider configured status.
  Implemented: URL/token storage, `/healthz` + `/v1/models` status.
- **Providers**: list from the router registry, masked API-key input, save via SSH to
  the VPS secret/env store, `configured` / `not configured` status.
  Implemented: read-only provider status derived from the router's existing `/healthz`
  (loaded IDs + load-time warnings); Unknown when the router reports nothing. Provider
  details let you store an API key locally (Keystore-encrypted, per provider, masked,
  never read back). Not yet: sending the key to the router or VPS over SSH.
- **Ollama**: status and models. Not yet implemented.

Notes:

- Tariffia Router remains a separate backend/core. This panel is a thin control plane:
  it talks to the router over HTTP and to the VPS over SSH.
- Provider API keys are never committed, never logged, and never shown in full once saved.
- The router core is not modified by this project.

Status: **early skeleton** — Kotlin + Compose app with four screens. Settings stores
the router URL and, via the Android Keystore, the router token (masked input with a
temporary Show/Hide, never logged, never shown again once saved, clearable with
confirmation). Home/Status is a dashboard: `GET /healthz`
(status + provider summary) and `GET /v1/models` (count + IDs) with
`Authorization: Bearer`. VPS/SSH stores the profile and
Keystore-encrypted private key/passphrase, pins the server host key after explicit
user confirmation, and can Test Connection via JSch. Providers shows the status the
router reports through `/healthz` (no new endpoint; Unknown when unreported) and lets
you store a per-provider API key locally in the Android Keystore (masked, never read
back, never sent anywhere yet). Sending the key to the router/VPS and Ollama are not
implemented yet.
