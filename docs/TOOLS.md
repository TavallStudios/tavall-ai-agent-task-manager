# Tools

This file lists the current first-party commands and MCP tools shipped by this repository.

Scope:

- includes only custom AgentTaskManager commands and MCP tools
- excludes third-party downstream MCP tools such as `filesystem`, `git`, and `ripgrep`
- downstream generic git mutation tools are intentionally not part of the AgentTaskManager commit workflow
- excludes MCP prompts, resources, and HTTP endpoints

## MCP Runtime Notes

- `TAVALL_AI_STDIO_PROTOCOL=auto|content-length|line` controls stdio framing.
- `TAVALL_AI_STDIO_DISABLE_DB=1` disables embedded Postgres for stdio-only smoke runs (persistence-backed tools are unavailable).
- `AGENT_TASK_MANAGER_CODEX_MCP_SERVER_BIN_DIR=/path/to/mcp-servers/bin` sets the preferred local MCP binaries path.

## CLI Commands

Source: `CliCommandService.java`, `AgentTaskManagerLauncher.java`

| Command | What it does |
| --- | --- |
| `validate [repoPath]` | Runs the validation pipeline for a repository and prints the full validation report JSON. |
| `scan` | Prints the current runtime summary JSON from the runtime. |
| `patch-check <diffFile>` | Reads a diff file and checks whether the patch stays within allowed scope. |
| `run-agent <taskId> <repoPath> [agentId]` | Claims one queued worker task for a batch and runs it through the local Codex worker transport. |
| `run-workers <taskId> <repoPath> [agentPrefix]` | Repeatedly claims and runs queued worker tasks for a batch until no tasks remain. |
| `run-autonomy-cycle [repoPath]` | Runs one autonomous orchestration cycle and prints the resulting report (legacy rollback flow; disabled by default unless `app.orchestration.legacy-autonomy-enabled=true`). |
| `print-rule-report [repoPath]` | Runs validation and prints each rule violation entry. |
| `example-report` | Runs validation against the current repo and prints the example report payload. |
| `serve-mcp-stdio` | Starts the central MCP server over stdio using the current MCP catalog. |
| `remote-mcp-smoke [baseUrl] [username] [password]` | Performs the remote MCP smoke flow against the configured or provided endpoint. |
| `reindex-knowledge` | Rebuilds the configured knowledge index. |
| `reindex-codebases` | Rebuilds configured codebase semantic indexes. |
| `search-knowledge <queryText> [limit]` | Searches the indexed knowledge base and prints the results. |

Notes:

- `AgentTaskManagerLauncher` currently dispatches every command above except `reindex-codebases`.
- `reindex-codebases` exists in `CliCommandService`, but it is not in the launcher allowlist yet.

## Central MCP Tools

These are the first-party tools registered into the main `tavall-ai` MCP catalog.

### Artifact Tools

| Tool | What it does |
| --- | --- |
| `readArtifact` | Reads an artifact body by artifact id. |
| `writeArtifact` | Writes a generic artifact with optional metadata. |
| `storeTaskArtifact` | Stores a task artifact without extra metadata. |
| `loadTaskArtifacts` | Loads artifacts for a task, optionally narrowed to a worker task. |
| `storeDiffArtifact` | Stores a diff artifact for a worker task. |
| `loadDiffArtifact` | Loads a diff artifact body by artifact id. |

### Cache Tools

| Tool | What it does |
| --- | --- |
| `cacheTaskContext` | Warms task context cache for a task. |
| `getCachedTaskContext` | Reads cached task context for a task. |
| `cacheValidationSummary` | Runs validation to warm the validation summary cache. |
| `getCachedValidationSummary` | Reads cached validation summary data. |
| `invalidateTaskCache` | Invalidates cached task context for a task. |
| `warmDashboardCache` | Warms dashboard cache state. |

### Context Tools

| Tool | What it does |
| --- | --- |
| `loadTaskContext` | Loads the aggregated task context payload. |
| `loadArchitectureRules` | Reads `docs/RULES.md`. |
| `loadUniversalGuidance` | Reads `docs/UNIVERSAL.md`. |
| `loadExamples` | Reads `docs/EXAMPLES.md`. |
| `loadValidationHistory` | Loads stored validation history for a task. |
| `loadDashboardSummary` | Loads the current dashboard summary payload. |
| `loadChatState` | Loads a chat thread detail payload. |
| `searchSemanticContext` | Searches stored semantic context by project and query text. |
| `loadSiblingTaskSummaries` | Loads sibling worker summaries for a task. |
| `storeSharedTaskContext` | Stores a shared task context entry. |
| `loadSharedTaskContext` | Loads shared task context entries for a task. |

### Orchestration Tools

| Tool | What it does |
| --- | --- |
| `startDelegationRun` | Starts the canonical Codex delegation run. |
| `appendDelegationRunEvent` | Appends timeline events (`spawn`, `wait`, `result`, `failure`) to a delegation run. |
| `loadDelegationRun` | Loads one delegation run with timeline steps. |
| `listDelegationRuns` | Lists delegation runs, optionally filtered by status. |
| `completeDelegationRun` | Completes a delegation run with final status and summary. |
| `createTaskBatch` | Deprecated compatibility adapter that maps legacy batch creation to a delegation run and returns deprecation metadata. |
| `claimWorkerTask` | Deprecated compatibility adapter that maps legacy worker-claim state to a delegation run event. |
| `assignWorkerTask` | Deprecated compatibility adapter that maps legacy assignment state to a delegation run event. |
| `reassignWorkerTask` | Deprecated compatibility adapter that maps legacy reassignment state to a delegation run event. |
| `completeWorkerTask` | Deprecated compatibility adapter that maps legacy completion state to a delegation run event. |
| `failWorkerTask` | Deprecated compatibility adapter that maps legacy failure state to a delegation run event. |
| `deadLetterWorkerTask` | Deprecated compatibility adapter that maps legacy dead-letter state to a delegation run event. |
| `createCleanupReviewTask` | Creates a cleanup review task for a diff artifact. |
| `submitWorkerCheckIn` | Stores a worker progress check-in. |
| `heartbeatWorker` | Refreshes a worker heartbeat. |
| `markWorkerDead` | Marks a worker dead after heartbeat timeout. |
| `registerWorker` | Registers a worker session. |
| `updateWorkerLease` | Refreshes worker lease and heartbeat. |
| `registerCleanupAgent` | Registers the cleanup agent session. |
| `submitCleanupReview` | Stores a cleanup review result. |
| `markCleanupReviewRequired` | Marks cleanup review as required. |
| `markCleanupApproved` | Marks cleanup review approved. |
| `markCleanupRejected` | Marks cleanup review rejected with findings. |
| `mergeWorkerOutputs` | Deprecated compatibility adapter that merges legacy worker summaries and records a delegation-run compatibility event. |
| `approvePatch` | Approves a patch decision after validation and cleanup review. |
| `rejectPatch` | Rejects a patch decision and requires rework. |
| `storeOverseerDecision` | Stores an overseer decision record. |
| `storeRunSummary` | Stores the final run summary for a batch. |
| `runAutonomousCycle` | Deprecated legacy autonomous scheduler entrypoint; disabled unless rollback flag is enabled. |
| `publishDashboardUpdate` | Warms and republishes dashboard summary state. |

### Repo Workflow Tools

| Tool | What it does |
| --- | --- |
| `planGitCommit` | Renders the deterministic branch name, verbose commit subject/body, and grouping recommendation for the current concern without mutating git state. |
| `prepareGitBranch` | Creates or switches to the deterministic branch for the current concern. |
| `createGitCommit` | Stages and commits the current concern through the first-party git workflow with a verbose body. |
| `stageSharedRepoSnapshot` | Decodes an uploaded repository archive and stages it into a local temporary workspace. |

### Validation Tools

| Tool | What it does |
| --- | --- |
| `runArchUnitValidation` | Runs ArchUnit validation rules. |
| `runSpoonValidation` | Runs Spoon source-shape validation. |
| `runJavaLintValidation` | Runs deterministic Java lint checks (Checkstyle, PMD, Error Prone). |
| `runIntegrationTests` | Runs repository integration tests. |
| `validatePatchScope` | Checks whether a diff stays within allowed patch scope. |
| `storeValidationReport` | Runs validation and stores the report. |
| `runCleanupDiffReview` | Runs cleanup diff review for a cleanup review id. |

### Tavall Memory Plane Tools

Sources: `MemoryKnowledgeToolHandler`, `VectorMemoryCanonicalToolHandler`, and `VectorMemoryToolHandler` in `tavall-ai-vector-memory-tools`.

Use the repository skills under `.agents/skills/` for workflow guidance. `memoryContext` is the default compiled retrieval path; the lower-level semantic/provider tools are for focused follow-up rather than mandatory fan-out on every task.

When `@Tavall Cloud v2` is available, use its CONTROL-owned workspaces/sandboxes/jobs and service/node/log surfaces for current repository/runtime/deployment evidence around these memory calls. Memory tools remain owned by Tavall AI; Cloud is the execution and verification substrate, not another memory authority.

| Tool | What it does |
| --- | --- |
| `memoryContext` | Compiles exact Postgres memory, Qdrant semantic recall, Graphify structural context, Graphiti temporal context, and configured knowledge into one provider-neutral hydration for a task. |
| `recordMemory` | Persists one intentional distilled durable memory with provenance. Ordinary turns are not a durable write path. |
| `memoryRelated` | Queries the configured Graphify provider for current structural/code relationships. |
| `codeImpact` | Queries Graphify for pull-request blast radius against the current graph. |
| `memoryHistory` | Queries the configured Graphiti provider for temporal facts and relationship history. |
| `recordTemporalFact` | Writes one already-verified deterministic temporal relationship through Graphiti without LLM fact extraction. |
| `memoryProviderStats` | Returns process-local provider call counts, degradation counts, latency, and context-volume telemetry. |
| `searchRelatedContexts` | Searches focused project semantic context in Qdrant. Treat matches as candidate evidence. |
| `searchPriorFixes` | Searches semantic task-history collections for prior fixes and reviews. Treat matches as candidate evidence until verified. |
| `storeTaskEmbedding` | Explicitly chunks, embeds, and stores a distilled task semantic document. This is not the canonical durable-memory promotion boundary. |
| `attachSemanticContextToTask` | Stores structured shared task context and explicitly indexes the supplied distilled semantic body. |
| `storeSemanticDocument` | Lower-level semantic indexing tool for explicit semantic payload storage. |
| `searchSemanticChunks` | Searches semantic chunks and returns stored payload text/code rather than vectors. |
| `searchSemanticHistory` | Searches semantic task-history chunks. |
| `searchKnowledgeIndex` | Searches indexed knowledge content. |
| `reindexSemanticKnowledge` | Rebuilds the configured semantic knowledge index. |
| `reindexConfiguredCodebases` | Rebuilds semantic indexes for configured codebases. |
| `attachSemanticDocumentToTask` | Attaches a semantic document to a task and indexes it. |
| `purgeLegacySemanticCollection` | Deletes the legacy shared Qdrant collection. Use only as part of an intentional migration/cleanup workflow. |

#### Memory tool selection

- Start substantive Tavall work with `memoryContext` when memory tools are available.
- Use `searchPriorFixes` / `searchRelatedContexts` for deeper semantic investigation, not as substitutes for canonical exact state.
- Use `memoryRelated` / `codeImpact` for current structural evidence.
- Use `memoryHistory` for temporal/architecture history.
- Use `recordMemory` only for verified reusable conclusions with correct scope and provenance.
- Use `recordTemporalFact` only for already-verified temporal relationships.
- Use `memoryProviderStats` during provider/acceptance validation or when diagnosing degraded/slow retrieval.

### Harness Validator Tools (`tavall-ai-clean-java-harness`)

These tools are exposed by the central MCP, but the implementation is backed by bundled local harness and validator code rather than a separate harness server process.

| Tool | What it does |
| --- | --- |
| `intakeHarnessTask` | Accepts parent work and persists shared harness state. |
| `routeHarnessTask` | Routes parent work into typed worker plans without creating tasks. |
| `loadHarnessState` | Loads shared task, agent, persistence, and dashboard state for a harness task. |
| `runHarnessToolBundle` | Runs the harness bundle broker for repo, worker, or language context (`java-context` compatibility alias). |
| `runHarnessApprovalGate` | Runs cleanup, validation, integration-test, and patch-scope gates for a worker task. |
| `loadCleanJavaTaskContext` | Builds deterministic clean Java task context for harness use. |
| `runCleanJavaHarness` | Runs the full deterministic clean Java harness (lint + Spoon + ArchUnit with cycle extraction). |
| `runJavaIntegrationHarness` | Runs the deterministic Java integration-test harness. |

## Dedicated Clean Java MCP Tools

These tools are custom, but only `clean-java-mcp` remains a dedicated stdio server. The `tavall-ai-clean-java-harness` module is now bundled as a local validator/runtime dependency instead of a standalone MCP server.

### Clean Java MCP Server

| Tool | What it does |
| --- | --- |
| `loadCleanJavaRules` | Loads the clean Java rules document. |
| `loadCleanJavaMcpTaskContext` | Builds deterministic clean Java task context for MCP use. |
| `runCleanJavaArchUnit` | Runs clean Java ArchUnit rules against a repo. |
| `runCleanJavaSpoon` | Runs clean Java Spoon source-shape rules. |
| `validateCleanJavaPatchScope` | Validates clean Java patch scope. |
