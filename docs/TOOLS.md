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

Source: [CliCommandService.java](/F:/workspace/AgentTaskManager/tavall-ai-core/src/main/java/com/agenttaskmanager/app/cli/CliCommandService.java), [AgentTaskManagerLauncher.java](/F:/workspace/AgentTaskManager/tavall-ai-app/src/main/java/com/agenttaskmanager/app/AgentTaskManagerLauncher.java)

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

Source: [ArtifactToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-artifact-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/artifact/ArtifactToolHandler.java)

| Tool | What it does |
| --- | --- |
| `readArtifact` | Reads an artifact body by artifact id. |
| `writeArtifact` | Writes a generic artifact with optional metadata. |
| `storeTaskArtifact` | Stores a task artifact without extra metadata. |
| `loadTaskArtifacts` | Loads artifacts for a task, optionally narrowed to a worker task. |
| `storeDiffArtifact` | Stores a diff artifact for a worker task. |
| `loadDiffArtifact` | Loads a diff artifact body by artifact id. |

### Cache Tools

Source: [CacheToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-cache-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/cache/CacheToolHandler.java)

| Tool | What it does |
| --- | --- |
| `cacheTaskContext` | Warms task context cache for a task. |
| `getCachedTaskContext` | Reads cached task context for a task. |
| `cacheValidationSummary` | Runs validation to warm the validation summary cache. |
| `getCachedValidationSummary` | Reads cached validation summary data. |
| `invalidateTaskCache` | Invalidates cached task context for a task. |
| `warmDashboardCache` | Warms dashboard cache state. |

### Context Tools

Source: [ContextToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-context-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/context/ContextToolHandler.java)

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

Source: [DecisionToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-orchestration-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/orchestration/DecisionToolHandler.java), [TaskPoolToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-orchestration-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/orchestration/TaskPoolToolHandler.java), [WorkerAgentToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-orchestration-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/orchestration/WorkerAgentToolHandler.java)

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

Source: [GitWorkflowToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-repo-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/repo/GitWorkflowToolHandler.java), [SharedRepoSnapshotToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-repo-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/repo/SharedRepoSnapshotToolHandler.java)

| Tool | What it does |
| --- | --- |
| `planGitCommit` | Renders the deterministic branch name, verbose commit subject/body, and grouping recommendation for the current concern without mutating git state. |
| `prepareGitBranch` | Creates or switches to the deterministic `domain-system-user-vN` branch for the current concern. |
| `createGitCommit` | Stages and commits the current concern through the first-party git workflow with a verbose body. |
| `stageSharedRepoSnapshot` | Decodes an uploaded repository archive and stages it into a local temporary workspace. |

### Validation Tools

Source: [ValidationToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-validation-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/validation/ValidationToolHandler.java)

| Tool | What it does |
| --- | --- |
| `runArchUnitValidation` | Runs ArchUnit validation rules. |
| `runSpoonValidation` | Runs Spoon source-shape validation. |
| `runJavaLintValidation` | Runs deterministic Java lint checks (Checkstyle, PMD, Error Prone). |
| `runIntegrationTests` | Runs repository integration tests. |
| `validatePatchScope` | Checks whether a diff stays within allowed patch scope. |
| `storeValidationReport` | Runs validation and stores the report. |
| `runCleanupDiffReview` | Runs cleanup diff review for a cleanup review id. |

### Vector Memory Tools

Source: [VectorMemoryToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-vector-memory-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/vectormemory/VectorMemoryToolHandler.java), [VectorMemoryCanonicalToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-vector-memory-tools/src/main/java/com/agenttaskmanager/app/mcp/tools/vectormemory/VectorMemoryCanonicalToolHandler.java)

| Tool | What it does |
| --- | --- |
| `storeSemanticDocument` | Chunks, embeds, and stores semantic payloads for a task. |
| `searchSemanticChunks` | Searches semantic chunks and returns stored payload text or code. |
| `searchSemanticHistory` | Searches semantic task history chunks. |
| `searchKnowledgeIndex` | Searches indexed knowledge content. |
| `reindexSemanticKnowledge` | Rebuilds the configured semantic knowledge index. |
| `reindexConfiguredCodebases` | Rebuilds semantic indexes for configured codebases. |
| `attachSemanticDocumentToTask` | Attaches a semantic document to a task and indexes it. |
| `purgeLegacySemanticCollection` | Deletes the legacy shared Qdrant collection. |
| `storeTaskEmbedding` | Stores chunked task embedding payloads into vector memory. |
| `searchRelatedContexts` | Searches related semantic context chunks. |
| `loadRelatedSemanticContext` | Loads related semantic context for the active project. |
| `searchPriorFixes` | Searches semantic task history for prior fixes and reviews. |
| `attachSemanticContextToTask` | Stores shared task context and indexes the same body through the semantic pipeline. |

### Harness Validator Tools (`tavall-ai-clean-java-harness`)

Source: [CleanJavaHarnessToolHandler.java](/F:/workspace/AgentTaskManager/tavall-ai-clean-java-harness/src/main/java/com/agenttaskmanager/app/mcp/CleanJavaHarnessToolHandler.java), [CleanJavaHarnessTools.java](/F:/workspace/AgentTaskManager/tavall-ai-clean-java-harness/src/main/java/com/agenttaskmanager/app/mcp/cleanjava/CleanJavaHarnessTools.java)

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

These tools are custom, but only `clean-java-mcp` remains a dedicated stdio server. The `tavall-ai-clean-java-harness` module (module path `tavall-ai-clean-java-harness`) is now bundled as a local validator/runtime dependency instead of a standalone MCP server.

### Clean Java MCP Server

Source: [CleanJavaMcpTools.java](/F:/workspace/AgentTaskManager/tavall-ai-clean-java-mcp/src/main/java/com/agenttaskmanager/app/mcp/cleanjava/CleanJavaMcpTools.java)

| Tool | What it does |
| --- | --- |
| `loadCleanJavaRules` | Loads the clean Java rules document. |
| `loadCleanJavaMcpTaskContext` | Builds deterministic clean Java task context for MCP use. |
| `runCleanJavaArchUnit` | Runs clean Java ArchUnit rules against a repo. |
| `runCleanJavaSpoon` | Runs clean Java Spoon source-shape rules. |
| `validateCleanJavaPatchScope` | Validates clean Java patch scope. |


