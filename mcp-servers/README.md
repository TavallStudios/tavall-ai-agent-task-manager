Repo-local fallback MCP binaries live in `mcp-servers/bin`.

Resolution order for local fallback servers is:

1. `mcp-servers/bin` inside this cloned repo
2. `AGENT_TASK_MANAGER_CODEX_MCP_SERVER_BIN_DIR`
3. the system `PATH`

The intended use is fallback-only. The default runtime path is the central `tavall-ai` MCP over local stdio, with repo-context tool execution brokered to the configured remote MCP endpoint.

