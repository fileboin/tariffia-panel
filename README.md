# Tariffia Panel

Android control panel for Tariffia Router, VPS/SSH and provider configuration.

Planned MVP (partially implemented):

- **VPS / SSH**: host, port, user, private key, host-key verification, Test SSH Connection.
  Implemented: profile + Keystore-encrypted key/passphrase, mandatory host-key pinning
  with explicit enrollment, Test Connection (JSch). Not yet: running commands/deploy.
- **Router**: URL, token, `/healthz`, `/v1/models`, provider configured status.
  Implemented: URL/token storage, `/healthz` + `/v1/models` status.
- **Providers**: list from the router registry, masked API-key input, save via SSH to
  the VPS secret/env store, `configured` / `not configured` status. Not yet implemented.
- **Ollama**: status and models. Not yet implemented.

Notes:

- Tariffia Router remains a separate backend/core. This panel is a thin control plane:
  it talks to the router over HTTP and to the VPS over SSH.
- Provider API keys are never committed, never logged, and never shown in full once saved.
- The router core is not modified by this project.

Status: **early skeleton** — Kotlin + Compose app with four screens. Settings stores
the router URL and, via the Android Keystore, the router token (masked input, never
logged, never shown again once saved). Home/Status checks `GET /healthz` and lists
`GET /v1/models` with `Authorization: Bearer`. VPS/SSH stores the profile and
Keystore-encrypted private key/passphrase, pins the server host key after explicit
user confirmation, and can Test Connection via JSch. Provider keys and Ollama are not
implemented yet.
