# Harness

This file describes the current harness model in AgentTaskManager.

The short version:

- the harness is not a separate MCP server
- it attaches to Codex by injecting the central `tavall-ai` MCP server into the Codex command at startup
- it also attaches by wrapping the Codex prompt with policy, memory, and tool-usage guidance
- once Codex is running, the harness brokers repository and task context through the central MCP
- after Codex exits, the harness runs bundled local validation and approval gates against the produced diff

## What The Harness Actually Is

Today the harness is a runtime behavior composed of:

- deterministic Codex process configuration
- prompt and tool-policy shaping
- central MCP tool access through `tavall-ai`
- bundled local validation and approval logic
- optional remote repo-context brokering when remote MCP is enabled

It is not a second MCP process that Codex connects to separately.

The main local validator facade is:

- `org.tavall.ai.app.cleanjava.CleanJavaHarnessValidator`

The main worker runtime entrypoint that drives Codex and then runs the validator is:

- `org.tavall.ai.app.orchestration.LocalCodexWorkerTransport`

## How The Harness Attaches To A Codex Instance

The harness attaches in two layers.

### 1. Startup Injection Into The Codex Command

AgentTaskManager does not rely on a user-global Codex config.

Instead, it builds the Codex command itself and injects deterministic `-c` settings before `codex exec` starts. That happens in:

- `org.tavall.ai.app.bridge.CodexDeterministicConfigService`
- `org.tavall.ai.app.bridge.CodexExecCommandFactory`
- `org.tavall.ai.app.orchestration.LocalCodexWorkerTransport`

The injected config includes:

- `model_reasoning_effort`
- `mcp_servers.tavall-ai.command`
- optional `mcp_servers.tavall-ai.args`
- optional `mcp_servers.tavall-ai.env.*`
- optional `--add-dir` paths

That is the primary attach point. When Codex starts, it already has a configured MCP server named `tavall-ai`.

If local central stdio is enabled, that server resolves to the local app jar with `serve-mcp-stdio`.

If remote repo brokering is enabled, the same local central MCP stays attached to Codex, but that central MCP forwards repo-context work through the remote MCP HTTP path when needed.

### 2. Prompt Envelope Injection

AgentTaskManager also attaches itself by shaping the prompt Codex receives.

That prompt envelope is built by:

- `org.tavall.ai.app.bridge.CodexExecCommandFactory`
- `org.tavall.ai.app.orchestration.WorkerPromptFactory`
- `org.tavall.ai.app.bridge.BridgePromptMemoryService`

The prompt currently injects:

- execution mode
- deterministic execution policy
- memory policy
- tool-combination guidance
- contextual tool policy
- final response contract
- retrieved memory context
- the actual user or worker request

So the harness attaches to Codex in two concrete ways:

1. Codex starts with the central MCP already injected.
2. Codex receives a controlled prompt contract that tells it how to use that MCP.

## What The Harness Does Once Codex Is Running

### Exposes One Central MCP Surface

Codex talks to one first-party MCP server:

- `tavall-ai`

That central MCP exposes the first-party tool catalog for:

- artifacts
- context
- orchestration
- validation
- vector memory
- repo snapshot staging
- harness bundle and approval operations

The harness does not spin up a standalone `tavall-ai-clean-java-harness` (`clean-java-harness` compatibility alias) MCP server anymore.

### Brokers Repo And Task Context

The main harness broker call is:

- `runHarnessToolBundle`

That tool is backed by `org.tavall.ai.app.harness.tools.HarnessToolBundleService`.

Depending on the requested bundle, it can assemble:

- harness state
- aggregated task context
- shared task context
- semantic context
- dashboard summary
- clean Java rules
- deterministic clean Java task context

For repo-context requests, it fans out downstream repository inspection calls in parallel.

The downstream tools currently used behind the broker are:

- `filesystem:list_directory`
- `git:git_status`
- `git:git_diff_unstaged`
- `ripgrep:advanced-search`
- `ripgrep:list-files`

For language-context requests (including the `java-context` compatibility alias) it can also request Java file listings.

### Can Broker Repo Context Locally Or Remotely

When remote repo brokering is enabled:

- the local central MCP snapshots the repo with `SharedRepoSnapshotService`
- it uploads that snapshot with `stageSharedRepoSnapshot`
- the remote MCP runs `runHarnessToolBundle(repo-context)` against the staged workspace
- the merged repo-context result comes back through the local central MCP to Codex

When remote repo brokering is not enabled:

- `HarnessToolBundleService` uses local downstream MCP tools directly through `DownstreamMcpToolClientService`

So the harness can run fully local, but it prefers remote repo-context execution when the remote MCP path is configured.

### Observes The Live Codex Run

For worker execution, `LocalCodexWorkerTransport` is responsible for the Codex lifecycle around the task.

It currently:

1. prepares the workspace
2. builds the deterministic Codex command
3. starts `codex exec`
4. streams stdout and stderr
5. parses Codex JSON events
6. records observed tool calls for tool-policy auditing
7. reads the final message from `--output-last-message`
8. captures the git diff
9. stores output and diff artifacts
10. stores prompt memory about the run

The harness therefore does not "attach later" to a live Codex session. It starts Codex in a controlled environment and monitors the full run from the beginning.

## What The Harness Does After Codex Exits

After the Codex process finishes, the bundled local validator path runs.

`LocalCodexWorkerTransport` calls:

- `CleanJavaHarnessValidator.runApprovalGate(...)`

That delegates into:

- `HarnessApprovalService`
- `CleanJavaDeterministicHarnessService`
- `ValidationPipelineService`

The approval gate currently performs fail-closed checks for:

- cleanup review when the worker type requires it
- deterministic clean Java validation
- integration tests when required
- patch-scope validation
- final task-status resolution

This means the harness is not only a tool broker. It is also the enforcement layer that decides whether a Codex-produced patch is accepted, rejected, or marked for rework.

## Local Validation Stages Used By The Harness

The clean Java validation path is:

- `CleanJavaHarnessValidator`
- `HarnessApprovalService`
- `CleanJavaDeterministicHarnessService`

`CleanJavaDeterministicHarnessService` currently runs:

1. deterministic clean Java task-context build
2. Java lint validation (Checkstyle, PMD, Error Prone)
3. Spoon source-shape validation
4. ArchUnit validation
5. merged validation report storage
6. cycle-check extraction from the ArchUnit report

Integration testing is delegated through:

- `ValidationPipelineService.runIntegrationTests(...)`

So the bundled local validator uses:

- Java lint (Checkstyle, PMD, Error Prone)
- Spoon
- ArchUnit
- integration tests
- cleanup review
- patch-scope validation
- validation-report storage

## First-Party Tools The Harness Relies On

Codex sees these first-party harness-adjacent MCP tools through the central server:

- `runHarnessToolBundle`
- `planGitCommit`
- `prepareGitBranch`
- `createGitCommit`
- `runHarnessApprovalGate`
- `loadHarnessState`
- `loadCleanJavaTaskContext`
- `runCleanJavaHarness`
- `runJavaIntegrationHarness`

In practice, the most important runtime tool for Codex-driven repository work is still:

- `runHarnessToolBundle`

Other first-party MCP tools the harness expects Codex to use when appropriate include:

- `loadTaskContext`
- `loadSharedTaskContext`
- `loadValidationHistory`
- `searchPriorFixes`
- `searchRelatedContexts`
- `storeTaskArtifact`
- `storeDiffArtifact`
- `planGitCommit`
- `prepareGitBranch`
- `createGitCommit`

The local validator may also use validation services equivalent to:

- `runJavaLintValidation`
- `runSpoonValidation`
- `runArchUnitValidation`
- `runIntegrationTests`
- `validatePatchScope`

Those are runtime services used during gating, even when Codex itself did not invoke those MCP tools directly.

## Current End-To-End Flow

The current harness flow around a Codex worker run is:

1. AgentTaskManager prepares a workspace for the worker.
2. AgentTaskManager builds a Codex command with deterministic MCP injection.
3. Codex starts with the central `tavall-ai` MCP already attached.
4. Codex receives a prompt envelope with memory, policy, and tool guidance.
5. Codex calls central MCP tools, usually starting with repo and task context retrieval.
6. `runHarnessToolBundle` brokers repository inspection locally or through the remote MCP snapshot path.
7. Codex uses the first-party git workflow tools to plan the branch, switch onto it, and create verbose concern-scoped commits.
8. AgentTaskManager captures tool calls, stdout, stderr, final message, branch/commit state, and git diff from the starting revision.
9. AgentTaskManager runs bundled local approval and validation after Codex exits.
10. The worker is marked completed, failed, or needs rework based on the gate results.

## What The Harness Does Not Do

The current harness does not:

- run as a separate `tavall-ai-clean-java-harness` or `clean-java-harness` MCP server
- depend on SSH mounts or live cross-host filesystem access
- attach itself to a pre-existing Codex process after the fact

Its current model is:

- start Codex deterministically
- inject the central MCP up front
- broker repo context during the run
- validate and gate the patch locally after the run



