# AgentTaskManager

AgentTaskManager is a Spring Boot control plane for local and remote Codex orchestration. It now includes:

- a Java MCP server built with the official MCP Java SDK
- a multi-agent task pool with overseer, worker, and cleanup roles
- ArchUnit and Spoon validation pipelines with a shared report model
- Postgres, Redis, MongoDB, and Qdrant persistence boundaries
- an AbstractCache-derived `cache` package for hot orchestration, validation, and dashboard caching
- dashboard APIs and UI panels for chats, workers, batches, validation, and patch outcomes
- a CLI entrypoint for validation, scan, worker execution, and stdio MCP serving

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
- `com.agenttaskmanager.app.orchestration`
  Task pooling, worker lifecycle, artifacts, cleanup review, and Codex worker transport.
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
  Semantic context storage and similarity search using a shared-dimension embedding chain: Gemini first, local FastEmbed runner second, hash fallback last.
- `cache`
  Memory-first TTL caches in front of dashboard, validation, worker, and context reads.

## CLI

Run the CLI with:

```bash
mvn -q -DskipTests package
java -cp target/classes:target/dependency/* com.agenttaskmanager.app.cli.AgentTaskManagerCli <command>
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

Default embedding order is `gemini,local,hash`. The local runner points at `scripts/fastembed_embed.py`, which expects `fastembed` to be installed on the host:

```bash
python3 -m pip install fastembed
```

`app.qdrant.collection` remains the legacy fallback collection for older flows, while new project-scoped memory now lands in `app.qdrant.project-collection-prefix` collections and optional indexed knowledge lands in `app.qdrant.knowledge-collection-prefix` collections.

## MCP

- HTTP MCP endpoint: `app.mcp.endpoint` with default `/mcp`
- stdio MCP entrypoint: CLI command `serve-mcp-stdio`
- prompts: `overseerAgent`, `workerAgent`, `cleanupAgent`
- resources: `README.md`, `AGENTS.md`, `RULES.md`, `ARCHITECTURE.md`, `EXAMPLES.md`
- tools: task pool, worker lifecycle, context, validation, artifact, retrieval, cache, and decision tools

Remote MCP smoke test:

```bash
AGENT_TASK_MANAGER_PASSWORD=... ./scripts/test_remote_mcp.sh
AGENT_TASK_MANAGER_PASSWORD=... java -cp 'target/classes:target/dependency/*' \
  com.agenttaskmanager.app.cli.AgentTaskManagerCli remote-mcp-smoke
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

The v1 platform builds as one codebase. Worker execution runs through `codex exec` with model `gpt-5.3-codex` by default when available, while the MCP and dashboard surfaces expose the orchestration state for local and remote use.
