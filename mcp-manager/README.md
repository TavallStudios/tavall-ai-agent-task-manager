# MCP Manager

`mcp-manager` is a standalone cross-platform MCP configuration manager implemented as a single Go binary with an embedded local web UI.

It is intentionally separate from the WinUI desktop client in this repository. AgentTaskManager still owns required MCP/Codex injection in runtime code. This tool manages user-facing and optional/shared MCP profiles, previews rendered config, writes managed metadata, and keeps rollback history.

## Current v1 capabilities

- discover MCP config documents from:
  - `config.toml`
  - `.codex/config.toml`
  - `.mcp.json`
  - `mcp-config.json`
  - `.ai/mcp/*.json`
  - `.mcp/*.json|*.toml`
  - `mcp-config/*.json|*.toml`
  - nested MCP config files under direct `mcp-servers/`, `.mcp/`, `.ai/mcp/`, and `mcp-config/` roots
- merge repo roots with the current user Codex home so global MCP installs show up beside workspace MCP files
- parse JSON and TOML config files into one normalized profile model
- surface both MCP servers and their mapped tool inventories in separate sidebar sections
- expose delegated tool detail pages that stay wired to the parent MCP settings form
- expose a schema-driven plugin registry with:
  - `generic`
  - `tavall-ai`
  - `chrome-devtools`
- map known tool catalogs for:
  - `tavall-ai`
  - `chrome-devtools`
  - `filesystem`
  - `git`
  - `ripgrep`
  - `tree-sitter`
- extract typed settings from real MCP launch config for:
  - AgentTaskManager env-driven remote bridge settings
  - Chrome DevTools wrapper args and launch flags
- expose direct nested JSON editors for manager-owned settings and extra server config
- preview rendered document output before save
- write manager-owned metadata in:
  - `x-mcp-manager` for JSON
  - `x_mcp_manager` for TOML
- create local backup history and allow restore
- emit live console activity logs for discovery, preview, save, restore, and API/UI requests
- expose both:
  - local HTML UI
  - local JSON API

## AgentTaskManager compatibility

- The standalone manager does not inject `AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS`.
- AgentTaskManager runtime code remains the source of truth for required/fail-closed MCP behavior.
- ATM-specific custom settings such as clean-code mode and tool bundle presets are stored as typed manager metadata and optional env overlays, not as a replacement for runtime-owned orchestration defaults.
- Chrome DevTools settings are not metadata-only; they are extracted from and written back into the actual launcher args that drive `chrome-devtools-mcp`.

## Run

When Go is available:

```bash
go run ./cmd/mcp-manager serve
go run ./cmd/mcp-manager discover
```

Optional flags:

```bash
go run ./cmd/mcp-manager serve --listen 127.0.0.1:47811 --roots "F:\\workspace\\AgentTaskManager,C:\\Users\\you\\.codex"
```

`--roots` replaces the default discovery roots. Use it when you want the dashboard to manage only a curated MCP hub directory.

## Export Bundle

On Windows you can export a clean Documents bundle that manages only the copied user MCP configs instead of scanning the repo workspace:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\export-bundle.ps1
```

That creates a self-contained bundle in `Documents\MCP Manager Bundle` with:

- `mcp-manager.exe`
- `launch-mcp-manager.cmd`
- `mcp-servers\`
- `state\`

## API

- `GET /api/discovery`
- `GET /api/overview`
- `GET /api/documents/{id}`
- `POST /api/documents/{id}/preview`
- `POST /api/documents/{id}/save`
- `GET /api/documents/{id}/backups`
- `POST /api/documents/{id}/restore`

## Notes

- v1 prefers deterministic rendering over comment-preserving round-trips.
- When a source format cannot be preserved losslessly, the manager writes explicit manager-owned metadata instead of pretending to preserve untouched structure.
- Secrets are still represented as config/env references in v1. OS keychain integration is intentionally deferred.

