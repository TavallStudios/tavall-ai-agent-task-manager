# Harness Workflow

Use AgentTaskManager as the default harness path for repository work across repos.

## Default Order

1. Prefer the installed AgentTaskManager plugin or the repo-local plugin launcher before other work so the central MCP surface is already available.
   If plugin-managed runtime is unavailable, continue with the repo-local launcher or remote `/mcp` flow instead of blocking on HTTP startup.
2. Call `runHarnessToolBundle(repo-context)` first.
   Use it for brokered repository inspection, filesystem listing, search, git status or diff context, and the harness-owned memory section for the active project.
3. Call `runHarnessToolBundle(worker-context)` when worker state, task state, shared context, or dashboard state matters.
   Expect it to include memory, memory status, Qdrant health, and repo sync status when the project is known.
4. Use retrieval and context tools when a task needs more than raw repo context:
   `loadTaskContext`, `loadValidationHistory`, `searchPriorFixes`, `searchRelatedContexts`, `loadSharedTaskContext`, `loadSiblingTaskSummaries`.
5. Use artifact tools when the run needs durable outputs:
   `storeTaskArtifact`, `storeDiffArtifact`, `loadTaskArtifacts`, `loadDiffArtifact`.
6. For Java work, call `loadCleanJavaTaskContext`, then `runHarnessToolBundle(java-context)`, then `runCleanJavaHarness`.
7. For repository mutation, switch to the git workflow reference and use `planGitCommit`, `prepareGitBranch`, and `createGitCommit`.
8. Expect `runHarnessApprovalGate` or the bundled approval path to enforce cleanup, validation, integration-test, and patch-scope checks after the run.
9. Expect the prompt transcript to show harness bootstrap, memory lookup, Java symbol preload, tool policy, observed tool calls, semantic sync, and git workflow status so you can verify the deterministic stages actually fired.

## Memory Expectations

- Treat memory as a harness stage that runs automatically before execution, not as an optional prompt habit.
- Repo-context and worker-context are the normal entry points for chat history, thread history, project semantic hits, and knowledge hits.
- Keep vector-memory and search tools for repair, inspection, or admin workflows, but do not treat them as the primary enforcement path.
- Expect changed repo files and Java symbol summaries to sync during execution and to be reconciled again after the run so committed edits stay searchable.

## Approval Expectations

- Workers do not self-approve.
- Cleanup, validation, and patch-scope review are fail-closed.
- Treat reported validation findings as required remediation, not optional suggestions.

## Fallback Expectations

- Prefer remote repo-context brokering first.
- Use local downstream MCP when remote brokering is unavailable.
- Use direct local probing only after the harness path cannot satisfy the operation.
- Do not use raw shell git mutation as the normal path.
- If a repo-backed prompt produces a diff, the harness expects exactly one new git workflow commit for that prompt.
- Do not rely on plugin prompt text as the enforcement layer for memory; the harness owns that responsibility.
