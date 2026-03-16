# AgentTaskManager

AgentTaskManager is a multi-module Spring Boot control plane for local and remote Codex orchestration. It now includes:

- a Java MCP server built with the official MCP Java SDK
- a harness core that accepts parent work, routes typed workers, assembles shared state, and gates approvals
- a brokered harness tool bundle that fans out downstream MCP calls in parallel and returns one merged response
- specialized worker types for code, cleanup, computer-use, and retrieval jobs
- ArchUnit and Spoon validation pipelines with a shared report model
- Postgres, Redis, MongoDB, and Qdrant persistence boundaries
- an AbstractCache-derived `cache` package for hot orchestration, validation, and dashboard caching
- dashboard APIs and UI panels for chats, workers, batches, validation, and patch outcomes
- a CLI entrypoint for validation, scan, worker execution, and stdio MCP serving

## Modules

- `agent-task-manager-core`
  Headless runtime services, MCP catalog wiring, validation, persistence, orchestration, and shared configuration.
- `spring-webview`
  Spring MVC delivery for the dashboard, static assets, security, and HTTP MCP transport.
- `agent-task-manager-clean-java-mcp`
  Dedicated stdio MCP executable for clean Java rules and validation tools.
- `agent-task-manager-clean-java-harness`
  Dedicated stdio MCP executable for the harness-core intake, routing, state, approval, bundled tool brokering, and clean Java harness tools.
- `agent-task-manager-app`
  Final executable assembly that depends on the headless runtime, `spring-webview`, and both clean Java modules.

## Package Areas

- `com.agenttaskmanager.app.bridge`
  Codex bridge worker for queued prompt requests.
- `com.agenttaskmanager.app.cli`
  CLI entrypoint and command routing.
- `com.agenttaskmanager.app.dashboard`
  Dashboard summary service and DTOs.
- `com.agenttaskmanager.app.loader`
  Service-loader bootstrap and static access surface.
- `com.agenttaskmanager.app.model`
  Shared bridge, orchestration, validation, and API DTOs that are safe to reuse across layers.
- `com.agenttaskmanager.app.mcp`
  MCP server bootstrap, resources, prompts, and tool handlers.
  The dedicated `mcp.cleanjava` subpackage isolates the Clean Java MCP and harness tool implementations behind the existing handler surface.
- `com.agenttaskmanager.app.web`
  Servlet controllers and page delivery now live in the `spring-webview` module instead of `agent-task-manager-core`.
- `com.agenttaskmanager.app.orchestration`
  Task pooling, worker lifecycle, artifacts, cleanup review, and Codex worker transport.
- `com.agenttaskmanager.app.harness`
  Parent-task intake, typed worker routing, shared task and agent schemas, shared persistence and dashboard models, and approval gating.
- `com.agenttaskmanager.app.persistence`
  Postgres, Redis, MongoDB, and Qdrant adapters.
- `com.agenttaskmanager.app.validation`
  ArchUnit and Spoon validation plus patch-gate scoring.
- `cache`
  Typed caches for task context, validation summaries, dashboard state, semantic context, and worker sessions.

## Runtime Responsibilities

- Redis
  Hot queues, worker heartbeats, locks, and counters.
- Postgres
  Durable task batches, worker tasks, check-ins, cleanup reviews, validation reports, patch decisions, and dashboard views.
- MongoDB
  Artifact bodies and chat snapshots.
- Qdrant
  Semantic context storage and similarity search using a chunk-first flow: raw content is chunked, each chunk is embedded, and Qdrant stores the vector together with the original chunk payload and metadata.
- `cache`
  Memory-first TTL caches in front of dashboard, validation, worker, and context reads.

## Harness Core

- task intake
  Accept parent work, resolve repository context, and persist intake state.
- routing
  Split parent work into `CODE`, `CLEANUP`, `COMPUTER_USE`, and `RETRIEVAL` workers.
- orchestration
  Reuse the overseer and worker pool to schedule typed workers.
- state
  Build one shared task schema, agent schema, persistence model, and dashboard model for each harness task.
- approval
  Run the shared cleanup, validation, integration-test, and patch-scope gate before any worker result is accepted.

## CLI

Build everything with:

```bash
mvn -q package
```

Run the main app jar with no command to start the web server, or pass a CLI command to reuse the same executable:

```bash
java -jar agent-task-manager-app/target/agent-task-manager-app-0.1.0-SNAPSHOT.jar
java -jar agent-task-manager-app/target/agent-task-manager-app-0.1.0-SNAPSHOT.jar <command>
```

Commands:

- `validate [repoPath]`
- `scan`
- `patch-check <diffFile>`
- `run-agent <taskId> <repoPath> [agentId]`
- `run-workers <taskId> <repoPath> [agentPrefix]`
- `print-rule-report [repoPath]`
- `example-report`
- `serve-mcp-stdio`
- `remote-mcp-smoke [baseUrl] [username] [password]`

Logging overrides:

- `AGENT_TASK_MANAGER_LOG_LEVEL_ROOT`
- `AGENT_TASK_MANAGER_LOG_LEVEL_SPRING`
- `AGENT_TASK_MANAGER_LOG_LEVEL_APP`

Embedding overrides:

- `GEMINI_API_KEY`
- `AGENT_TASK_MANAGER_EMBEDDING_PROVIDER_ORDER`
- `AGENT_TASK_MANAGER_EMBEDDING_DIMENSIONS`
- `AGENT_TASK_MANAGER_GEMINI_EMBEDDING_MODEL`
- `AGENT_TASK_MANAGER_LOCAL_EMBEDDING_COMMAND`
- `AGENT_TASK_MANAGER_LOCAL_EMBEDDING_MODEL`
- `AGENT_TASK_MANAGER_LOCAL_EMBEDDING_TIMEOUT_SECONDS`

Default embedding order is `gemini,local,hash`. The Gemini default model is `gemini-embedding-2-preview`, the runtime default dimension is `1536`, and the local runner points at `scripts/fastembed_embed.py`, which expects `fastembed` to be installed on the host:

```bash
python3 -m pip install fastembed
```

`app.qdrant.collection` is now migration-only for purging old data. New semantic writes land in project-scoped collections under `app.qdrant.project-collection-prefix`, and indexed knowledge lands in `app.qdrant.knowledge-collection-prefix`.

Semantic retrieval flow:

- raw content is chunked by content type before embedding
- each chunk is embedded with the retrieval-purpose-specific Gemini task type
- Qdrant stores the embedding together with the original chunk text/code and metadata
- query text is embedded separately and searches Qdrant
- workers and harness services consume the retrieved payload chunk text, not the raw vectors

## MCP

- HTTP MCP endpoint: `app.mcp.endpoint` with default `/mcp`
- stdio MCP entrypoint: CLI command `serve-mcp-stdio`
- prompts: `overseerAgent`, `workerAgent`, `cleanupAgent`
- resources: `README.md`, `AGENTS.md`, `RULES.md`, `ARCHITECTURE.md`, `EXAMPLES.md`
- tools: task pool, worker lifecycle, context, validation, artifact, retrieval, cache, and decision tools

The dedicated `clean-java-harness` stdio server exposes the curated harness surface:

- `intakeHarnessTask`
- `routeHarnessTask`
- `loadHarnessState`
- `runHarnessApprovalGate`
- `runHarnessToolBundle`
- `loadCleanJavaTaskContext`
- `runCleanJavaHarness`
- `runJavaIntegrationHarness`

Codex worker runs now inject `clean-java-harness` as the default required MCP server. Repository inspection and retrieval should flow through `runHarnessToolBundle`, which fans out to filesystem, ripgrep, and git on the harness host in parallel and returns one merged payload.
For Java work, the harness should build deterministic context first with `loadCleanJavaTaskContext`, then the worker should draft code, then `runCleanJavaHarness` applies Spoon source-shape checks first and ArchUnit plus cycle checks second before approval.

Remote MCP smoke test:

```bash
AGENT_TASK_MANAGER_PASSWORD=... ./scripts/test_remote_mcp.sh
AGENT_TASK_MANAGER_PASSWORD=... java -jar \
  agent-task-manager-app/target/agent-task-manager-app-0.1.0-SNAPSHOT.jar remote-mcp-smoke
```

The shell script and the CLI smoke command both perform the official streamable HTTP flow against the configured endpoint. The CLI path uses the official Java MCP client and normalizes path-based deployments such as `https://docs.tavall.org/agent-task-manager` plus `/mcp` into `https://docs.tavall.org` plus `/agent-task-manager/mcp`.

The smoke flow covers:

- `initialize`
- `notifications/initialized`
- `tools/list`
- `tools/call` for `loadDashboardSummary`

## Validation

- ArchUnit
  Production package boundaries, forbidden dependencies, cycle detection across runtime slices, cache boundaries, and MCP adapter isolation. Shared `model`, `config`, and `loader` registry code are treated as boundary-supporting infrastructure instead of normal cycle slices.
- Spoon
  Class size, naming, dependency-access patterns, null-branch style, inner-class restrictions, JavaDoc checks, mocked test detection, and inline-call heuristics.

## Current Status

The platform now builds as a multi-module Maven project with a dedicated `spring-webview` servlet module and separate clean Java MCP and harness executables. Worker execution still runs through `codex exec` with model `gpt-5.3-codex` by default when available, but Codex now depends on the harness MCP surface by default while the harness brokers downstream repository tools in parallel and exposes one shared task and approval model for local and remote use.

The MCP surface now also exposes canonical semantic/context tool names that match the orchestration contract directly: `storeTaskEmbedding`, `searchRelatedContexts`, `searchPriorFixes`, `loadRelatedSemanticContext`, and `attachSemanticContextToTask`. Live dashboard state is also available as MCP resources under `state://dashboard/summary`, `state://dashboard/workers`, `state://dashboard/chats`, and `state://dashboard/batches`.
