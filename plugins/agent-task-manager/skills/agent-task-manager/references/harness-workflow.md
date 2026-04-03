# Harness Workflow

Use AgentTaskManager as the default harness path for repository work across repos.

## Default Order

1. Prefer the installed AgentTaskManager plugin or the repo-local plugin launcher before other work so the central MCP surface is already available.
   If plugin-managed runtime is unavailable, continue with the repo-local launcher or remote `/mcp` flow instead of blocking on HTTP startup.
2. Call `runHarnessToolBundle(repo-context)` first.
   Use it for brokered repository inspection, filesystem listing, search, and git status or diff context.
3. Call `runHarnessToolBundle(worker-context)` when worker state, task state, shared context, or dashboard state matters.
4. Use retrieval and context tools when a task needs more than raw repo context:
   `loadTaskContext`, `loadValidationHistory`, `searchPriorFixes`, `searchRelatedContexts`, `loadSharedTaskContext`, `loadSiblingTaskSummaries`.
5. Use artifact tools when the run needs durable outputs:
   `storeTaskArtifact`, `storeDiffArtifact`, `loadTaskArtifacts`, `loadDiffArtifact`.
6. For Java work, call `loadCleanJavaTaskContext`, then `runHarnessToolBundle(java-context)`, then `runCleanJavaHarness`.
7. For repository mutation, switch to the git workflow reference and use `planGitCommit`, `prepareGitBranch`, and `createGitCommit`.
8. Expect `runHarnessApprovalGate` or the bundled approval path to enforce cleanup, validation, integration-test, and patch-scope checks after the run.

## Approval Expectations

- Workers do not self-approve.
- Cleanup, validation, and patch-scope review are fail-closed.
- Treat reported validation findings as required remediation, not optional suggestions.

## Fallback Expectations

- Prefer remote repo-context brokering first.
- Use local downstream MCP when remote brokering is unavailable.
- Use direct local probing only after the harness path cannot satisfy the operation.
- Do not use raw shell git mutation as the normal path.
