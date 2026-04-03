# ARCHITECTURE

## Boundaries

Build modules:

- `agent-task-manager-core`
  Shared headless runtime components, MCP catalog infrastructure, validation, persistence, runtime summaries, and orchestration code.
- `spring-webview`
  The compatibility MCP HTTP adapter module. It retains Spring-hosted `/mcp` transport for tests and phased migration but no longer ships dashboard pages, login views, or non-MCP REST APIs.
- `agent-task-manager-clean-java-mcp`
  The dedicated stdio MCP module for clean Java rule loading and source-shape validation tools.
- `agent-task-manager-clean-java-harness`
  The dedicated stdio MCP module for the harness-core intake, routing, state, approval, bundled tool brokering, and clean Java harness tools without the servlet web surface on its classpath.
- `agent-task-manager-app`
  The final app module that assembles the shared runtime, the standalone embedded MCP HTTP runtime, and both clean Java modules into one executable.

- `mcp`
  MCP prompts, resources, tools, and server bootstrap.
  Clean Java validation and harness tool implementations live under a dedicated `mcp.cleanjava` subpackage while preserving the existing MCP handler surface.
- `orchestration`
  Overseer flow, task pool, worker lifecycle, cleanup review, artifacts, shared context, and Codex worker transport.
- `harness`
  Parent-task intake, typed worker routing, shared task and agent schemas, shared persistence and runtime models, and approval gating.
- `validation`
  ArchUnit and Spoon validators, integration-test runner, scoring, and report storage.
- `persistence`
  Store-specific adapters for Postgres, Redis, MongoDB, and Qdrant.
- `dashboard`
  Aggregated read models for MCP resources and remote observers.
- `loader`
  Service loader registration and dependency-access contracts.
- `model`
  Shared neutral records and exceptions for bridge, orchestration, validation, and API payloads.
- `cache`
  Typed TTL-aware caches shared by orchestration, validation, and runtime summary code.

`loader` is the explicit exception to the normal cycle rule. Dependency-access interfaces call into the service loader by design, so ArchUnit cycle checks focus on the runtime slices that should remain clean: `bridge`, `cli`, `dashboard`, `mcp`, `orchestration`, `persistence`, `service`, and `validation`.

## Main Flow

1. The harness intake accepts parent work and resolves repository context.
2. The routing layer creates typed worker plans for code, cleanup, computer-use, and retrieval work.
3. The overseer creates a batch and queues those worker tasks.
4. A worker session claims and receives a lease.
5. `LocalCodexWorkerTransport` executes `codex exec` inside an isolated worktree.
6. Codex uses the harness MCP surface, and the harness fans out repository tool calls in parallel when bundle tools are invoked.
7. Worker output and diff artifacts are stored.
8. The shared harness approval gate runs cleanup review, validation, integration tests when required, and patch-scope checks.
9. The overseer stores decisions and patch outcomes.
10. The shared harness state model plus MCP surfaces expose the latest state.

## Cooperative Automation Flow

1. A local provider bridge registers one bridge session with `cooperativeAutomation=true`.
2. The control plane validates that `intrusiveInput=false` before it queues automation commands.
3. Commands such as `hytale.join-server`, `hytale.open-asset-editor`, or `hyrhythm.start-gameplay` are stored in Postgres against that session.
4. The local automation bridge polls and claims one queued command at a time.
5. The bridge forwards the command to a loopback provider contract instead of issuing raw OS mouse movement.
6. The provider returns structured state or evidence, and AgentTaskManager stores the final result on the command row.

## Prompt Thread Memory Flow

1. Incoming MCP interactions can carry an explicit `threadKey`; otherwise the runtime derives one from the MCP session id plus project or repo scope.
2. Before a tool, prompt, or resource handler dispatches, the runtime performs one authoritative exact-thread lookup plus thread-scoped semantic search using that key.
3. The interaction envelope receives thread memory first, then broader project memory, then optional indexed knowledge.
4. The accepted interaction is persisted immediately as a `prompt-thread-message` and linked to a durable interaction row.
5. Memory lookup summaries, final results or failures, and compact thread snapshots are persisted after execution so remote recall stays current.
6. Old chats can be searched through Postgres by key or preview text and through Qdrant by thread-scoped semantic recall.

## Hytale Learning Flow

1. A Hytale learning session is created against a machine profile, server target, scenario id, and optional bridge session.
2. Action traces, timeline frames, and visual anchors are stored through the shared Postgres and Mongo persistence boundaries.
3. Redis hot state tracks the current automation phase plus focus-safety markers for the active learning session.
4. Playbooks capture reusable ordered actions plus expected anchors and failure-recovery branches.
5. Promotion decisions fail closed: unstable traces remain artifacts only, while stable summaries and approved playbooks are promoted into project-scoped semantic memory.
6. Retrieval returns executable approved playbooks first, then promoted recovery memory, then visual anchors, then general semantic notes for the same machine and scenario scope.

## Semantic Retrieval

- New semantic storage is chunk-first. The runtime splits docs, chats, code, diffs, and run summaries before embedding, then stores the vector together with the original chunk payload and metadata in Qdrant.
- `app.qdrant.collection` is migration-only for old data cleanup, while new runtime writes go to `app.qdrant.project-collection-prefix` collections and knowledge vectors go to `app.qdrant.knowledge-collection-prefix` collections.
- `EmbeddingProviderChain` keeps one Qdrant vector size across providers.
- `GeminiEmbeddingProvider` is the primary path with `gemini-embedding-2-preview`.
- `LocalCommandEmbeddingProvider` is the remote-safe fallback and defaults to the bundled FastEmbed runner.
- `HashEmbeddingService` remains the final fail-safe so retrieval tools do not hard-fail when the external providers are unavailable.
- Query routing uses retrieval-purpose-specific embeddings: `RETRIEVAL_DOCUMENT` for stored chunks, `RETRIEVAL_QUERY` for general search, and `CODE_RETRIEVAL_QUERY` for natural-language code lookup.

## MCP Surfaces

- standalone embedded HTTP streamable transport under `/mcp`
- stdio MCP via CLI
- resources for docs
- prompts for overseer, worker, and cleanup roles
- tool groups for task pooling, worker state, shared context, validation, artifacts, retrieval, cache, and decisions
- the dedicated `clean-java-harness` group now exposes a single harness surface for intake, routing, state, brokered tool bundles, approval, and deterministic clean Java validation
- deterministic Java work now follows one staged loop: build clean-Java task context, draft the patch, run Spoon source-shape checks, run ArchUnit architecture and cycle checks, then pass cleanup review and approval gates

The default runtime path starts embedded Tomcat directly from the app module and registers the official MCP Java SDK servlet without Spring MVC in the request path. A Spring-hosted MCP adapter remains in the repo as a compatibility layer for existing integration tests and migration safety.

## Remote Path

The standalone app runtime exposes the MCP servlet endpoint remotely by default. Local stdio and remote HTTP modes share the same tool catalog and internal service boundaries, while the compatibility Spring adapter remains available for phased migration and test coverage.
