# Standalone MCP Manager

The repository now includes a new top-level Go project at `mcp-manager/` for standalone MCP server discovery and settings management.

## Why it is separate

- the WinUI desktop app already owns session/auth/runtime orchestration
- MCP config is a cross-cutting concern shared by Codex, workspace overrides, repo-local examples, and AgentTaskManager profiles
- ATM runtime still needs deterministic required MCP injection in code

## Current boundaries

- `mcp-manager/` discovers and edits optional/shared MCP config documents
- `tavall-ai-*` runtime modules continue to own required MCP transport and fail-closed orchestration behavior
- ATM-specific presets and clean-code settings live in typed manager metadata and optional env overlays

## First shipped plugin

`tavall-ai`

Typed settings:

- remote execution enabled
- remote base URL
- MCP endpoint
- remote username
- remote password
- downstream central server
- no-auth mode
- clean code mode
- tool bundle preset

The manager stores these settings in a schema-driven profile layer and writes them back through deterministic JSON/TOML rendering plus manager-owned metadata blocks.

Additional first-class plugin:

`chrome-devtools`

Typed settings:

- package name
- auto-connect
- usage statistics enabled
- performance CrUX enabled
- browser executable path

These settings are extracted from and rendered back into the actual launcher args, not kept as disconnected dashboard-only metadata.

## Current discovery UX

- repo-local roots and the current user Codex home are merged so local MCP installs show up even when the app is launched against one workspace root
- nested config files under `mcp-servers/`, `.mcp/`, `.ai/mcp/`, and `mcp-config/` are discovered recursively
- the sidebar now has separate sections for MCP servers and mapped tools
- clicking an MCP shows its mapped tool surface
- clicking a tool opens a delegated tool page while keeping the parent MCP settings live underneath


