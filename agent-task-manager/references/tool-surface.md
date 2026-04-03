# Tool Surface

These are the high-value first-party MCP tools this skill expects Codex to use when working through AgentTaskManager.

## Core Harness

- `runHarnessToolBundle`
  Use first for repo-context, worker-context, and Java-context assembly.
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

## Artifact Flow

- `storeTaskArtifact`
- `storeDiffArtifact`
- `loadTaskArtifacts`
- `loadDiffArtifact`

Use these when the run needs durable outputs, captured diffs, or artifact review.

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
