# Tariffia Panel

Android control panel for Tariffia Router, VPS/SSH and provider configuration.

Planned MVP (not implemented yet):

- **VPS / SSH**: host, port, user, private key, host-key verification, Test SSH Connection.
- **Router**: URL, token, `/healthz`, `/v1/models`, provider configured status.
- **Providers**: list from the router registry, masked API-key input, save via SSH to
  the VPS secret/env store, `configured` / `not configured` status.
- **Ollama**: status and models.

Notes:

- Tariffia Router remains a separate backend/core. This panel is a thin control plane:
  it talks to the router over HTTP and to the VPS over SSH.
- Provider API keys are never committed, never logged, and never shown in full once saved.
- The router core is not modified by this project.

Status: **early skeleton** — Kotlin + Compose app with four placeholder screens.
The Settings screen can store the router URL and, via the Android Keystore, the
router token (masked input, never logged, never shown again once saved). Router
HTTP calls (`/healthz`, `/v1/models`), SSH and provider keys are not implemented yet.
