# ARCHITECTURE

## Boundaries

Build modules:

- `tavall-ai-core`
  Shared headless runtime components, MCP catalog infrastructure, validation, persistence, runtime summaries, and orchestration code.
- `tavall-ai-spring-webview`
  The compatibility MCP HTTP adapter module. It retains Spring-hosted `/mcp` transport for tests and phased migration but no longer ships dashboard pages, login views, or non-MCP REST APIs.
- `tavall-ai-clean-java-mcp`
  The dedicated stdio MCP module for clean Java rule loading and source-shape validation tools.
- `tavall-ai-clean-java-harness` (module path: `tavall-ai-clean-java-harness`)
  The bundled harness module for intake, routing, state, approval, bundled tool brokering, and clean-code harness tools without the servlet web surface on its classpath.
- `tavall-ai-app`
  The final app module that assembles the shared runtime, the standalone embedded MCP HTTP runtime, and both clean Java modules into one executable.

Core package responsibilities:

- `mcp`
  MCP prompts, resources, tools, and server bootstrap. Clean Java validation and harness tool implementations live under a dedicated `mcp.cleanjava` subpackage while preserving the existing MCP handler surface.
- `memory`
  Canonical Tavall memory identity, policy, continuity, hydration, provider orchestration, and provider-neutral context compilation. External knowledge providers terminate here rather than leaking vendor contracts into agent code.
- `orchestration`
  Canonical Codex delegation-run flow, legacy task-pool compatibility adapters, worker lifecycle, cleanup review, artifacts, shared context, and Codex worker transport.
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
3. The canonical orchestration path starts one delegation run and persists timeline events (`spawn`, `wait`, `result`, `failure`).
4. `LocalCodexWorkerTransport` executes one parent `codex exec` inside an isolated worktree; Codex-native multi-agent fan-out handles sub-agents.
5. Codex uses the harness MCP surface, and the harness fans out repository tool calls in parallel when bundle tools are invoked.
6. Worker output and diff artifacts are stored.
7. The shared harness approval gate runs cleanup review, validation, integration tests when required, and patch-scope checks.
8. The overseer stores decisions and patch outcomes.
9. Legacy task-pool/autonomy tools remain as compatibility adapters that map into delegation runs and still project batch/worker state for dashboards.
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
2. Before a tool, prompt, or resource handler dispatches, the runtime performs one authoritative exact-thread Postgres lookup using that key, then compiles the broader configured semantic, structural, temporal, and knowledge sources once.
3. The interaction envelope receives thread memory first, then broader project memory, then optional indexed knowledge.
4. The accepted interaction is persisted immediately as a `prompt-thread-message` and linked to a durable interaction row.
5. Prompt requests and messages, including lookup summaries and final results or failures, remain persisted in Postgres for exact-thread history; raw prompt messages and snapshots are not written to Qdrant.
6. Durable memory should prefer distilled findings, compact snapshots, provenance, and source references over duplicating complete provider transcripts. Provider-native Codex/session files remain raw evidence that can be fetched when verbatim history is necessary.

## Hytale Learning Flow

1. A Hytale learning session is created against a machine profile, server target, scenario id, and optional bridge session.
2. Action traces, timeline frames, and visual anchors are stored through the shared Postgres and Mongo persistence boundaries.
3. Redis hot state tracks the current automation phase plus focus-safety markers for the active learning session.
4. Playbooks capture reusable ordered actions plus expected anchors and failure-recovery branches.
5. Promotion decisions fail closed: unstable traces remain artifacts only, while stable summaries and approved playbooks are promoted into project-scoped semantic memory.
6. Retrieval returns executable approved playbooks first, then promoted recovery memory, then visual anchors, then general semantic notes for the same machine and scenario scope.

## Semantic Retrieval

- Semantic storage is chunk-first. The runtime splits docs, chats, code, diffs, and run summaries before embedding, then stores the vector together with the original chunk payload and metadata in Qdrant.
- `app.qdrant.collection` is migration-only for old data cleanup, while new runtime writes go to profile-isolated project and knowledge collections.
- The default embedding provider is local FastEmbed/ONNX using `BAAI/bge-small-en-v1.5` at 384 dimensions.
- Gemini and hash embeddings remain explicit operator-selected providers. They are not silently appended as fallback providers to the default local semantic space.
- A configured Qdrant failure does not silently become process-local in-memory persistence. In-memory Qdrant behavior is reserved for intentionally unconfigured local/test runtimes.
- Collection names include the embedding provider/model/dimension profile so incompatible vector spaces cannot be mixed.
- Query routing uses retrieval-purpose-specific embeddings: `RETRIEVAL_DOCUMENT` for stored chunks, `RETRIEVAL_QUERY` for general search, and `CODE_RETRIEVAL_QUERY` for natural-language code lookup.
- Semantic reranking combines vector score with lexical overlap, Java-symbol relevance, payload filters, recency, domain priority, and content type. Native sparse/BM25 collections require their own migration/profile validation before being enabled as a production default.

## Memory Knowledge Retrieval

The memory owner compiles several deliberately different retrieval geometries instead of making one store pretend to know everything:

- Postgres and Redis-backed exact memory provide durable/current state and hot continuity.
- Qdrant provides associative semantic recall and prior-fix/context candidates; it is supplemental retrieval, not canonical truth.
- Graphify is a replaceable structural provider over rebuildable workspace code graphs. It answers current topology, dependency, file/line, and pull-request blast-radius questions.
- Graphiti is a replaceable temporal provider for curated evolving facts, supersession, incidents, architecture decisions, and historical relationships. Already-verified structured relationships use direct triplet writes rather than paying for fact extraction from prose.
- Git, pull requests, Codex/provider session files, runtime logs, and similar artifacts remain raw evidence sources fetched by reference when required instead of being copied wholesale into the memory plane.

`MemoryRetrievalService` remains the canonical hydration path. It resolves identity, loads exact memory, retrieves semantic candidates, and asks `MemoryContextAugmentationService` for configured structural and temporal context. External provider failures remain visible as degraded context instead of being silently represented as an empty successful lookup.

Provider telemetry records calls, degradation, latency, and returned context volume so Graphify/Graphiti adoption remains measurable and reversible.

See `docs/MEMORY_KNOWLEDGE_PLANE.md` and `seed/tavall-memory-dev/README.md` for deployment and seed details.

## MCP Surfaces

- standalone embedded HTTP streamable transport under `/mcp`
- stdio MCP via CLI
- resources for docs
- prompts for overseer, worker, and cleanup roles
- tool groups for canonical delegation-run orchestration, compatibility task pooling, worker state, shared context, validation, artifacts, retrieval, cache, and decisions
- memory knowledge tools: `memoryContext`, `memoryRelated`, `codeImpact`, `memoryHistory`, `recordTemporalFact`, and `memoryProviderStats`
- the dedicated `tavall-ai-clean-java-harness` group (with `clean-java-harness` compatibility alias) exposes a single harness surface for intake, routing, state, brokered tool bundles, approval, and deterministic clean-code validation
- deterministic Java work follows one staged loop: build clean-Java task context, draft the patch, run Spoon source-shape checks, run ArchUnit architecture and cycle checks, then pass cleanup review and approval gates

The default runtime path starts embedded Tomcat directly from the app module and registers the official MCP Java SDK servlet without Spring MVC in the request path. A Spring-hosted MCP adapter remains in the repo as a compatibility layer for existing integration tests and migration safety.

## Remote Path

The standalone app runtime exposes the MCP servlet endpoint remotely by default. Local stdio and remote HTTP modes share the same tool catalog and internal service boundaries, while the compatibility Spring adapter remains available for phased migration and test coverage.
