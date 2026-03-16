# ARCHITECTURE

## Boundaries

Build modules:

- `agent-task-manager-core`
  Shared headless runtime components, MCP catalog infrastructure, validation, persistence, dashboard, and orchestration code.
- `spring-webview`
  Spring servlet delivery for the dashboard, HTTP APIs, login pages, static assets, and HTTP MCP transport.
- `agent-task-manager-clean-java-mcp`
  The dedicated stdio MCP module for clean Java rule loading and source-shape validation tools.
- `agent-task-manager-clean-java-harness`
  The dedicated stdio MCP module for the harness-core intake, routing, state, approval, and clean Java harness tools without the servlet web surface on its classpath.
- `agent-task-manager-app`
  The final app module that assembles the shared runtime, `spring-webview`, and both clean Java modules into one executable.

- `web`
  HTTP delivery and dashboard APIs.
- `mcp`
  MCP prompts, resources, tools, and server bootstrap.
  Clean Java validation and harness tool implementations live under a dedicated `mcp.cleanjava` subpackage while preserving the existing MCP handler surface.
- `orchestration`
  Overseer flow, task pool, worker lifecycle, cleanup review, artifacts, shared context, and Codex worker transport.
- `harness`
  Parent-task intake, typed worker routing, shared task and agent schemas, shared persistence and dashboard models, and approval gating.
- `validation`
  ArchUnit and Spoon validators, integration-test runner, scoring, and report storage.
- `persistence`
  Store-specific adapters for Postgres, Redis, MongoDB, and Qdrant.
- `dashboard`
  Aggregated read models for UI and remote observers.
- `loader`
  Service loader registration and dependency-access contracts.
- `model`
  Shared neutral records and exceptions for bridge, orchestration, validation, and API payloads.
- `cache`
  Typed TTL-aware caches shared by orchestration, validation, and dashboard code.

`loader` is the explicit exception to the normal cycle rule. Dependency-access interfaces call into the service loader by design, so ArchUnit cycle checks focus on the runtime slices that should remain clean: `bridge`, `cli`, `dashboard`, `mcp`, `orchestration`, `persistence`, `service`, `validation`, and `web`.

## Main Flow

1. The harness intake accepts parent work and resolves repository context.
2. The routing layer creates typed worker plans for code, cleanup, computer-use, and retrieval work.
3. The overseer creates a batch and queues those worker tasks.
4. A worker session claims and receives a lease.
5. `LocalCodexWorkerTransport` executes `codex exec` inside an isolated worktree.
6. Worker output and diff artifacts are stored.
7. The shared harness approval gate runs cleanup review, validation, integration tests when required, and patch-scope checks.
8. The overseer stores decisions and patch outcomes.
9. The shared harness state model plus dashboard and MCP surfaces expose the latest state.

## Semantic Retrieval

- `QdrantContextStore` keeps `app.qdrant.collection` as a legacy fallback, but the runtime now writes project-scoped vectors into `app.qdrant.project-collection-prefix` collections and knowledge vectors into `app.qdrant.knowledge-collection-prefix` collections.
- `EmbeddingProviderChain` keeps one Qdrant vector size across providers.
- `GeminiEmbeddingProvider` is the primary path with `gemini-embedding-001`.
- `LocalCommandEmbeddingProvider` is the remote-safe fallback and defaults to the bundled FastEmbed runner.
- `HashEmbeddingService` remains the final fail-safe so retrieval tools do not hard-fail when the external providers are unavailable.

## MCP Surfaces

- HTTP streamable transport under `/mcp`
- stdio MCP via CLI
- resources for docs
- prompts for overseer, worker, and cleanup roles
- tool groups for task pooling, worker state, shared context, validation, artifacts, retrieval, cache, and decisions
- the dedicated `clean-java-harness` group now exposes a single harness surface for intake, routing, state, approval, and deterministic clean Java validation

## Remote Path

The same Spring Boot app can expose the MCP servlet endpoint remotely. Local stdio and remote HTTP modes share the same tool catalog and internal service boundaries.
