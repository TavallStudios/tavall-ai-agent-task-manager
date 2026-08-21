---
name: tavall-ai
description: Use when the AgentTaskManager plugin should own Tavall repo context, worker context, harness memory, Java validation, deterministic git workflow, local MCP registration, or shared task coordination across repositories.
---

# Agent Task Manager

Use this plugin-local skill when the AgentTaskManager plugin is installed.

## Runtime Ownership

1. Let the plugin-local `.mcp.json` own local AgentTaskManager MCP registration.
2. Prefer `scripts/ensure_operator_surface.py` only when a local HTTP operator surface is actually needed.
3. If the plugin-managed runtime is unavailable, fall back to the repo-local launchers and remote `/mcp` flow in [references/task-runtime.md](references/task-runtime.md).

## Workflow

1. Call `runHarnessToolBundle(repo-context)` first. It carries harness-owned memory with brokered repository inspection.
2. Call `runHarnessToolBundle(worker-context)` when worker or task state matters.
3. Use [references/tool-surface.md](references/tool-surface.md) for retrieval, artifact, approval, and validation tool selection.
4. For Java work, call `loadCleanJavaTaskContext`, then `runHarnessToolBundle(java-context)`, then `runCleanJavaHarness`.
5. For repository mutation, use [references/git-workflow.md](references/git-workflow.md): `planGitCommit` -> `prepareGitBranch` -> `createGitCommit`.
6. Expect chat-visible harness transcript messages for memory lookup, Java symbol preload, semantic sync, tool policy, observed tool calls, and final git outcome.
7. If the run changed a Tavall repository, **REQUIRED SUB-SKILL:** use `tavall-local-ci` after the final commit and before claiming completion or handing off the PR.

## Memory Ownership

1. Treat memory as harness-owned, not an optional agent tool choice.
2. Expect `repo-context` and `worker-context` results to include memory and sync status when the project is known.
3. Changed files and Java symbol summaries in managed repositories are synchronized during execution and reconciled after the run.

## Fallback Policy

- Prefer the plugin-managed `tavall-ai` MCP server before direct per-tool MCP injection.
- Let repo-context inspection prefer remote brokering, then local downstream MCP, then controlled local fallback.
- Treat direct shell/file probing as fallback-only when harness tools are unavailable or failing.
- Do not use raw shell git mutation as the primary path.
- For a diff-producing repo-backed prompt, collapse the intended change into one harness-owned git workflow commit unless the repository's staging model requires otherwise.
- Local tests do not replace the required exact-head Tavall CI completion step for Tavall repository work.

## Notes

- The plugin owns runtime registration and startup helpers; skills own workflow and policy.
- Keep commits concern-scoped and use descriptive commit bodies through the first-party MCP git workflow.
- If the repo root cannot be inferred automatically, set `AGENT_TASK_MANAGER_REPO_ROOT`.
