# AGENTS

Follow these rules when editing this repository:

- Keep package layouts coherent, but do not hardcode one required package root into new module or validation work.
- Keep classes under 300 lines. Split by concern early.
- Use the official Java MCP SDK for MCP transport, tools, prompts, and resources.
- Use Codex CLI as the execution backend for worker tasks.
- AgentTaskManager must configure Codex deterministically in code. Do not rely on user-global Codex config for required MCP servers.
- Prefer MCP filesystem, ripgrep, git, Qdrant, and the harness semantic/context tools for repository inspection and retrieval. Treat direct shell file searching as fallback-only.
- For substantive Tavall engineering work, use the repository memory skills under `.agents/skills/` instead of improvising memory-plane behavior:
  - `tavall-memory-bootstrap` for the initial `memoryContext` hydration and authority ordering.
  - `tavall-memory-investigation` for prior fixes, Graphify structure/PR impact, Graphiti history, and evidence correlation.
  - `tavall-memory-writeback` for intentional verified `recordMemory`, supersession, provenance, and deterministic temporal facts.
  - `tavall-memory-review` for independent PR/architecture/regression review using current source plus memory-plane evidence.
  - `tavall-memory-validation` for exact-head memory-plane validation, provider acceptance, restart persistence, outbox behavior, and ordinary-turn no-growth checks.
- Treat `memoryContext` as the canonical compiled memory hydration path. Do not repeatedly fan out to every provider when one hydration answers the task.
- Treat Qdrant semantic matches and prior-fix results as candidate context, not canonical truth. Verify material claims against current source/runtime state before acting or writing durable memory.
- `recordMemory` is an explicit promotion boundary, never an ordinary turn/chat/tool-call capture path. Store only distilled, verified, reusable conclusions with appropriate scope and provenance.
- Optional indexed knowledge lives in Qdrant under the configured `app.knowledge-index.knowledge-base`; keep the index/search path aligned with the CLI and MCP tools.
- Use Redis, Postgres, MongoDB, and Qdrant through the existing persistence boundaries.
- Postgres remains canonical for durable relational memory/state; Redis is hot/ephemeral; Qdrant is associative; Graphify is rebuildable structure; Graphiti is curated temporal knowledge.
- Use the `cache` package for caching concerns instead of creating parallel cache abstractions.
- Do not add Guice, Dagger, CDI, `jakarta.inject`, or service-locator getter chains.
- Prefer service-loader dependency access interfaces over raw dependency getters in orchestration code.
- Do not add mocked unit tests. Use integration tests that exercise the real application path.
- Keep cleanup review, validation, and patch gating fail-closed.
- Run compile and integration tests after meaningful changes.
- Keep docs, fixtures, MCP resources, skills, and tool contracts aligned with the implementation.
