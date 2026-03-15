# AGENTS

Follow these rules when editing this repository:

- Use the current package layout under `com.agenttaskmanager.app` and the top-level `cache` package.
- Keep classes under 300 lines. Split by concern early.
- Use the official Java MCP SDK for MCP transport, tools, prompts, and resources.
- Use Codex CLI as the execution backend for worker tasks.
- AgentTaskManager must configure Codex deterministically in code. Do not rely on user-global Codex config for required MCP servers.
- Prefer MCP filesystem, ripgrep, git, memory, and Qdrant access for repository inspection and retrieval. Treat direct shell file searching as fallback-only.
- Optional indexed knowledge lives in Qdrant under the configured `app.knowledge-index.knowledge-base`; keep the index/search path aligned with the CLI and MCP tools.
- Use Redis, Postgres, MongoDB, and Qdrant through the existing persistence boundaries.
- Use the `cache` package for caching concerns instead of creating parallel cache abstractions.
- Do not add Guice, Dagger, CDI, `jakarta.inject`, or service-locator getter chains.
- Prefer service-loader dependency access interfaces over raw dependency getters in orchestration code.
- Do not add mocked unit tests. Use integration tests that exercise the real application path.
- Keep cleanup review, validation, and patch gating fail-closed.
- Run compile and integration tests after meaningful changes.
- Keep docs, fixtures, and MCP resources aligned with the implementation.
