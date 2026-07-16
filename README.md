# AgentTaskManager

AgentTaskManager is a multi-module MCP-first control plane for local and remote Codex orchestration. It now includes:

- a Java MCP server built with the official MCP Java SDK
- a harness core that accepts parent work, routes typed workers, assembles shared state, and gates approvals
- a brokered harness tool bundle that fans out downstream MCP calls in parallel and returns one merged response
- specialized worker types for code, cleanup, computer-use, and retrieval jobs
- ArchUnit and Spoon validation pipelines with a shared report model
- Postgres, Redis, MongoDB, and Qdrant persistence boundaries
- an AbstractCache-derived `cache` package for hot orchestration, validation, and runtime caching
- a cooperative automation command queue for same-device bridges that must not move the real mouse or steal focus
- a CLI entrypoint for validation, scan, worker execution, and stdio MCP serving
- a standalone embedded `/mcp` runtime in the main app, plus a compatibility MCP HTTP adapter module kept for Spring-hosted tests and phased migration

## Quick Start

Clone the repo, then point your MCP client at one of the repo-local stdio launchers:

- Unix: `scripts/tavall-ai-mcp-stdio.sh`
- Windows: `scripts/tavall-ai-mcp-stdio.cmd`

These scripts resolve the repo root automatically and build the app jar on first run if it is missing. The only setup you need in your MCP client is the launcher path plus any optional remote repo-broker environment variables.

If you want Codex to own that registration through a local plugin instead of a raw MCP config file, install the repo-local plugin at `plugins/tavall-ai/`. The plugin wraps the same runtime through `.mcp.json`, keeps the ATM skill bundled with it, and provides `scripts/ensure_operator_surface.py` when an HTTP operator surface is needed.

Example MCP config files are included in:

- `mcp-config/tavall-ai.stdio.unix.example.json`
- `mcp-config/tavall-ai.stdio.windows.example.json`

### MCP Stdio Options

- `TAVALL_AI_STDIO_PROTOCOL=auto|content-length|line`
  Controls stdio framing auto-detect and overrides when needed. Default is `auto`.
- `TAVALL_AI_STDIO_DISABLE_DB=1`
  Disables embedded Postgres for stdio-only smoke tests or CI runs. Persistence-backed tools are unavailable in this mode.
- `AGENT_TASK_MANAGER_CODEX_MCP_SERVER_BIN_DIR=/path/to/mcp-servers/bin`
  Preferred location for local MCP binaries (git/ripgrep/etc) before falling back to `PATH`.

## Desktop Operator Surface

The first-party operator experience is now desktop-first (`clients/desktop/AgentTaskManager.Desktop`) with four surfaces:

- `Work`
- `Operations`
- `Remote`
- `Settings`

VS Code and IntelliJ companion modules are removed from first-party builds. Use the desktop surface for operational flows. Migration guidance lives in `docs/codex-client-platform/COMPANION_MIGRATION.md`.

## Modules

- `tavall-ai-core`
  Headless runtime services, MCP catalog wiring, validation, persistence, orchestration, and shared configuration.
- `tavall-ai-spring-webview`
  The compatibility MCP HTTP adapter artifact `tavall-ai-mcp-http`. It retains the Spring-hosted `/mcp` transport for tests and phased migration without the old dashboard, pages, or REST APIs.
- `tavall-ai-clean-java-mcp`
  Dedicated stdio MCP executable for clean Java rules and validation tools.
- `tavall-ai-clean-java-harness` (module path: `tavall-ai-clean-java-harness`)
  Bundled local validator/runtime facade for harness approval, bundled repo context, and deterministic clean-code validation with language-context support. It is not a standalone MCP server.
- `tavall-ai-artifact-tools`
  Domain module for artifact read/write MCP tools that the central MCP imports.
- `tavall-ai-cache-tools`
  Domain module for cache warming and cache-read MCP tools that the central MCP imports.
- `tavall-ai-context-tools`
  Domain module for task context, docs, runtime state, chat state, and shared-context MCP tools.
- `tavall-ai-computer-use-tools`
  Domain module for external runner registration, session orchestration, capture, vision, and input MCP tools.
- `tavall-ai-orchestration-tools`
  Domain module for task-pool, worker-lifecycle, cleanup, overseer decision, and autonomous-cycle MCP tools.
- `tavall-ai-repo-tools`
  Domain module for repository transfer, branch, and verbose commit MCP tools.
- `tavall-ai-validation-tools`
  Domain module for validation, patch-scope, integration-test, and cleanup-review MCP tools.
- `tavall-ai-vector-memory-tools`
  Domain module for vector-memory and Qdrant-backed MCP tools that the central MCP imports.
- `tavall-ai-app`
  Final executable assembly for the shared runtime, standalone embedded MCP HTTP server, clean Java MCP executable, and bundled harness validator.

## Package Areas

- `org.tavall.ai.app.cli`
  CLI entrypoint and command routing.
- `org.tavall.ai.app.dashboard`
  Dashboard summary service and DTOs.
- `org.tavall.ai.app.loader`
  Service-loader bootstrap and static access surface.
- `org.tavall.ai.app.model`
  Shared bridge, orchestration, validation, and API DTOs that are safe to reuse across layers.
- `org.tavall.ai.app.mcp`
  MCP server bootstrap, resources, prompts, and shared tool wiring.
  The dedicated `mcp.cleanjava` subpackage isolates the Clean Java MCP and harness tool implementations behind the existing handler surface.
- `org.tavall.ai.app.mcp.tools.*`
  Domain-scoped MCP tool modules that plug into the central catalog.
- `org.tavall.ai.app.orchestration`
  Codex delegation runs, compatibility task-pool adapters, artifacts, cleanup review, and Codex worker transport.
- `org.tavall.ai.app.harness`
  Parent-task intake, typed worker routing, shared task and agent schemas, shared persistence and dashboard models, and approval gating.
- `org.tavall.ai.app.persistence`
  Postgres, Redis, MongoDB, and Qdrant adapters.
- `org.tavall.ai.app.validation`
  ArchUnit and Spoon validation plus patch-gate scoring.
- `cache`
  Typed caches for task context, validation summaries, runtime state, semantic context, and worker sessions.

## Cooperative Automation

AgentTaskManager supports a separate bridge path for same-device automation commands that stay non-intrusive.

- sessions register `cooperativeAutomation`, `intrusiveInput`, and `automationCommands`
- MCP tools queue high-level commands such as `hytale.join-server`, `hytale.open-asset-editor`, and `hyrhythm.press-lane`
- a local bridge client polls those commands and forwards them to a loopback provider
- the provider is expected to use cooperative hooks or app-specific APIs instead of raw OS mouse control

The Hytale-specific cooperative catalog now also includes:

- `hytale.close-overlay`
- `hytale.open-creative-tools`
- `hytale.open-asset-editor`
- `hytale.asset-editor.navigate`
- `hytale.capture-timeline`
- `hytale.promote-memory`
- `hytale.list-playbooks`
- `hytale.execute-playbook`

## Prompt Thread Memory

Prompt threads are now first-class durable memory objects instead of just request metadata.

- `threadKey` can be supplied explicitly in MCP interaction metadata and is otherwise derived from MCP session plus project or repo scope.
- On every MCP tool, prompt, or resource interaction, AgentTaskManager checks the durable thread store by `threadKey`, then merges thread-scoped semantic memory with broader project and knowledge memory.
- Inbound interactions, memory lookups, final results, failures, and compact thread snapshots are persisted back into remote Postgres and Qdrant-backed memory.
- Old chats are searchable by exact key or semantic recall.

MCP tools:

- `searchPromptThreads`
- `searchPromptThreadMemory`

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
  Memory-first TTL caches in front of runtime, validation, worker, and context reads.

## Harness Core

- task intake
  Accept parent work, resolve repository context, and persist intake state.
- routing
  Split parent work into `CODE`, `CLEANUP`, `COMPUTER_USE`, and `RETRIEVAL` workers.
- orchestration
  Reuse the overseer and worker pool to schedule typed workers.
- state
  Build one shared task schema, agent schema, persistence model, and runtime summary model for each harness task.
- approval
  Run the shared cleanup, validation, integration-test, and patch-scope gate before any worker result is accepted.

## CLI

Build everything with:

```bash
mvn -q package
```

Run the main app jar with no command to start the standalone embedded MCP HTTP runtime, or pass a CLI command to reuse the same executable:

```bash
java -jar tavall-ai-app/target/tavall-ai-app-0.1.0-SNAPSHOT.jar
java -jar tavall-ai-app/target/tavall-ai-app-0.1.0-SNAPSHOT.jar <command>
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
- resources: `README.md`, `docs/AGENTS.md`, `docs/RULES.md`, `docs/UNIVERSAL.md`, `docs/ARCHITECTURE.md`, `docs/EXAMPLES.md`
- tools: delegation-run orchestration, legacy compatibility task-pool adapters, worker lifecycle, context, validation, artifact, retrieval, cache, computer-use, and decision tools

The default no-args app path now starts [StandaloneAgentTaskManagerServer.java](/F:/workspace/AgentTaskManager/tavall-ai-app/src/main/java/com/agenttaskmanager/app/StandaloneAgentTaskManagerServer.java), which hosts the official MCP Java SDK servlet directly on embedded Tomcat instead of relying on Spring MVC for `/mcp`. The Spring-hosted adapter remains in the repo as a compatibility module for the phase transition and existing web-based integration tests.

The central `tavall-ai` MCP currently also exposes the curated harness tool surface:

- `intakeHarnessTask`
- `routeHarnessTask`
- `loadHarnessState`
- `runHarnessApprovalGate`
- `runHarnessToolBundle`
- `planGitCommit`
- `prepareGitBranch`
- `createGitCommit`
- `loadCleanJavaTaskContext`
- `runCleanJavaHarness`
- `runJavaIntegrationHarness`

Canonical delegation orchestration tools:

- `startDelegationRun`
- `appendDelegationRunEvent`
- `loadDelegationRun`
- `listDelegationRuns`
- `completeDelegationRun`

Legacy orchestration tools (`createTaskBatch`, `claimWorkerTask`, `assignWorkerTask`, `reassignWorkerTask`, `completeWorkerTask`, `failWorkerTask`, `deadLetterWorkerTask`, `mergeWorkerOutputs`, and `runAutonomousCycle`) remain available as compatibility adapters and now return deprecation metadata plus the mapped delegation run id when available.

The central MCP also exposes the external runner orchestration surface for Hytale-first computer-use work:

- `registerComputerUseRunner`
- `listComputerUseRunners`
- `startComputerUseSession`
- `launchComputerUseProcess`
- `captureComputerUseWindow`
- `sendComputerUseInput`
- `waitForComputerUseVisionMatch`
- `stopComputerUseSession`

The `tavall-ai-clean-java-harness` module (module path `tavall-ai-clean-java-harness`) is now a bundled local validator/runtime dependency for future Codex wrapping and local validation flows. It is no longer launched as a separate MCP server process.

Codex worker runs now inject `tavall-ai` as the default downstream central MCP server. Repository inspection and retrieval should flow through `runHarnessToolBundle`, which fans out to filesystem, ripgrep, and git on the harness host in parallel and returns one merged payload.
Repository mutation should then use `planGitCommit`, `prepareGitBranch`, and `createGitCommit` so branch naming and verbose commit structure stay auditable inside the first-party MCP workflow.
If you need direct per-tool injection instead, clear `AGENT_TASK_MANAGER_CODEX_DOWNSTREAM_CENTRAL_SERVER` and set `AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS=<comma-separated-server-list>`.
For plug-and-play stdio usage, point your client at the repo-local launcher scripts under `scripts/`. They locate the jar relative to the cloned repo and build it when needed.
Remote MCP brokering is now the default behavior when the remote endpoint and credentials are present. Local third-party MCP binaries are fallback-only and are resolved from `mcp-servers/bin` in the cloned repo first, then from `AGENT_TASK_MANAGER_CODEX_MCP_SERVER_BIN_DIR`, then from the system `PATH`.
If you want to override the remote settings explicitly, set:

- `AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED=true`
- `AGENT_TASK_MANAGER_MCP_BASE_URL`
- `AGENT_TASK_MANAGER_MCP_ENDPOINT`
- `AGENT_TASK_MANAGER_USERNAME`
- `AGENT_TASK_MANAGER_PASSWORD`
- `AGENT_TASK_MANAGER_MCP_NO_AUTH_ENABLED=true` to expose `/mcp` without app-level auth when the deployment wants MCP-only access

In that mode, the local central MCP stages repository snapshots through the remote MCP, then runs `runHarnessToolBundle(repo-context)` against the staged workspace. That replaces SSH launchers, mount roots, and direct cross-host filesystem access with an application-managed HTTP transfer path.
For Java work, Codex should gather repo and task context through the central MCP first, and AgentTaskManager then runs bundled local Spoon and ArchUnit validation during approval instead of depending on a separate harness MCP process.

Remote MCP smoke test:

```bash
./scripts/test_remote_mcp.sh
java -jar \
  tavall-ai-app/target/tavall-ai-app-0.1.0-SNAPSHOT.jar remote-mcp-smoke
```

Add `AGENT_TASK_MANAGER_PASSWORD=...` when the remote endpoint still requires HTTP Basic auth. The shell script and the CLI smoke command both perform the official streamable HTTP flow against the configured endpoint. The CLI path uses the official Java MCP client and normalizes path-based deployments such as `https://docs.tavall.org/tavall-ai` plus `/mcp` into `https://docs.tavall.org` plus `/tavall-ai/mcp`.

The smoke flow covers:

- `initialize`
- `notifications/initialized`
- `tools/list`
- `tools/call` for `loadDashboardSummary`

## Hytale External Runner

Game-class automation is designed around a separate Windows runner, not the operator desktop. The runner hosts `clients/desktop/AgentTaskManager.AutomationHost` in HTTP mode, while the Java control plane persists runner registrations, session leases, artifacts, and Hytale scenario state in the normal Postgres, Redis, and Mongo boundaries.

The built-in Hytale scenario ids are:

- `hytale/launch-and-join-smoke`
- `hytale/gameplay-assets-visible`
- `hytale/chart-start-stable`
- `hytale/note-hit-interaction`

## Hytale Hybrid Learning Memory

The control plane now includes a Hytale-specific learning surface for same-device cooperative automation with remote durable memory semantics:

- Postgres stores Hytale learning sessions, action traces, timeline-frame metadata, visual-anchor metadata, playbooks, and promotion decisions.
- Mongo stores the heavy frame or anchor capture bodies through the existing artifact document store.
- Redis hot state tracks the active automation phase and focus-safe marker for live Hytale learning sessions.
- Qdrant remains the promotion target for curated semantic memory such as `hytale-playbook`, `hytale-recovery-note`, and `hytale-scenario-summary`.
- Only approved or pinned Hytale playbooks are executable; unapproved playbooks stay retrieval-only.

The Hytale profile is configured deterministically in code and properties under `app.computer-use.*`, including launcher path, client path, server target, visual anchors, and gameplay lane keybinds. External-runner setup details live in [docs/computer-use-runner.md](F:\workspace\AgentTaskManager\docs\computer-use-runner.md) and [AUTOMATION_SETUP.md](F:\workspace\AgentTaskManager\clients\desktop\AUTOMATION_SETUP.md).
The Hytale learning and runner flows are now intended to be driven through MCP tools rather than a first-party browser UI.

## Validation

- ArchUnit
  Production package boundaries, forbidden dependencies, cycle detection across runtime slices, cache boundaries, and MCP adapter isolation. Shared `model`, `config`, and `loader` registry code are treated as boundary-supporting infrastructure instead of normal cycle slices.
- Spoon
  Class size, naming, dependency-access patterns, null-branch style, inner-class restrictions, JavaDoc checks, mocked test detection, and inline-call heuristics.

## Current Status

The platform now builds as a multi-module Maven project with a shared runtime core, a standalone embedded MCP HTTP runtime in the main app, a compatibility MCP adapter module for migration and tests, a separate clean Java MCP executable, and a bundled clean Java harness validator module. Worker execution still runs through `codex exec` with model `gpt-5.3-codex` by default when available, and the harness logic remains available locally for approval, repo-context brokering, and deterministic Java validation without requiring a separate harness MCP process.

The MCP surface now also exposes canonical semantic/context tool names that match the orchestration contract directly: `storeTaskEmbedding`, `searchRelatedContexts`, `searchPriorFixes`, `loadRelatedSemanticContext`, and `attachSemanticContextToTask`. Runtime state is also available as MCP resources under `state://dashboard/summary`, `state://dashboard/workers`, `state://dashboard/chats`, and `state://dashboard/batches`.

For AgentTaskManager itself, custom memory no longer depends on the legacy file-backed `memory` MCP server. Prompt and task memory flow through the harness semantic pipeline, which chunks payloads, embeds them, stores them in Qdrant with metadata, and retrieves the original chunk text/code back into worker context.

Tool modules are now split by domain/concern instead of keeping every handler inside `tavall-ai-core`. The central MCP imports dedicated artifact, cache, context, orchestration, repo-workflow, validation, and vector-memory tool modules, leaving `tavall-ai-core` focused on shared runtime services and MCP infrastructure.




