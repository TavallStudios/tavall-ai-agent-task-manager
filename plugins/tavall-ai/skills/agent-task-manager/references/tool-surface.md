# Tool Surface

These are the high-value first-party MCP tools this skill expects Codex to use when working through AgentTaskManager.

## Core Harness

- `runHarnessToolBundle`
  Use first for repo-context, worker-context, and Java-context assembly.
  For known projects, `repo-context` and `worker-context` include the harness-owned `memory`, `memoryStatus`, and `qdrantHealth` fields.
  Prompt transcripts should show harness bootstrap, memory lookup, and tool-policy messages when this path is active.
- `runHarnessApprovalGate`
  Use when the task explicitly needs approval-gate status or validation outcome inspection.

## Retrieval And Context

- `loadTaskContext`
- `loadValidationHistory`
- `searchPriorFixes`
- `searchRelatedContexts`
- `loadSharedTaskContext`
- `loadSiblingTaskSummaries`

Use these when repo-context alone is not enough to understand the task, prior failures, or related implementation history.
The standalone retrieval tools are complementary to harness memory, not a replacement for it.
Background semantic indexing and reranking should keep committed repo files and Java symbol summaries visible through the same shared retrieval path.

## Artifact Flow

- `storeTaskArtifact`
- `storeDiffArtifact`
- `loadTaskArtifacts`
- `loadDiffArtifact`

Use these when the run needs durable outputs, captured diffs, or artifact review.
Changed repo files are synced into project memory automatically during the run and reconciled again after completion.

## Java Validation Flow

- `loadCleanJavaTaskContext`
- `runCleanJavaHarness`
- `runJavaIntegrationHarness`

Use these for deterministic Java-specific guidance and validation.

## Git Workflow

- `planGitCommit`
- `prepareGitBranch`
- `createGitCommit`

Use these for branch and commit mutation instead of raw shell git commands.
For a diff-producing repo-backed prompt, the harness expects exactly one new commit through this workflow and emits a transcript message with the final git outcome.
