---
name: agent-task-manager
description: Manage the AgentTaskManager plugin-managed cross-project harness and task runtime. Use when the installed Tavall AI plugin needs harness/runtime registration, repo-context or worker-context usage, Java validation and approval flow, deterministic git workflow, or shared Redis/Postgres task coordination across repos.
---

# Agent Task Manager

Use this plugin-local specialist when the AgentTaskManager harness/runtime workflow is required. `tavall-ai` remains the top-level Tavall AI operating entry point.

## Runtime Ownership

1. Let the installed Tavall AI plugin own local runtime registration.
2. Prefer the plugin/runtime-owned operator surface only when a local HTTP operator surface is actually needed.
3. If the plugin-managed runtime is unavailable, fall back to the repo-local launchers and remote `/mcp` flow described in [references/task-runtime.md](references/task-runtime.md).

## Workflow

1. Call `runHarnessToolBundle(repo-context)` first when that harness surface is exposed. It carries harness-owned memory/context for the active project alongside brokered repo inspection.
2. Call `runHarnessToolBundle(worker-context)` when worker or task state matters.
3. Use [references/tool-surface.md](references/tool-surface.md) for retrieval, artifact, approval, and validation tool selection.
4. For Java work, call `loadCleanJavaTaskContext`, then `runHarnessToolBundle(java-context)`, then `runCleanJavaHarness` when those capabilities are exposed.
5. For repository mutation, follow [references/git-workflow.md](references/git-workflow.md) and prefer the first-party deterministic Git workflow when available.
6. Expect chat-visible harness transcript messages for memory lookup, Java symbol preload, semantic sync, tool policy, observed tool calls, and final git workflow outcome when the harness owns execution.
7. If the run changed a Tavall repository, use `tavall-local-ci` after the final commit and before claiming completion or handing off the PR.

## Memory Ownership

1. Treat AgentTaskManager prompt-thread/task-runtime memory as harness-owned.
2. Do not claim that this specialist satisfies the broader `tavall-memory-plane` contract unless the memory-plane skill explicitly declares equivalence.
3. Keep vector/search tools for admin and debugging work, but do not rely on prompt wording alone to make harness memory happen.
4. Changed files and Java symbol summaries in managed repos should sync through the shared semantic path during execution and reconciliation.

## Fallback Policy

- Prefer the installed Tavall AI/AgentTaskManager runtime before direct per-tool MCP injection.
- Let repo-context inspection prefer remote brokering first, then local downstream MCP, and only then controlled local fallback.
- Treat direct shell file probing as fallback-only when harness tools are unavailable or failing.
- Do not use raw shell Git mutation as the primary path when the first-party workflow is available.
- Treat plugin prompts as guidance only. The harness owns its own memory enforcement and durable sync behavior.
- Local diagnostic tests do not replace the required exact-head Tavall CI completion step.

## Notes

- `tavall-ai` owns top-level Tavall AI routing. This skill owns the narrower AgentTaskManager harness/task-runtime workflow.
- Keep commits concern-scoped and preserve exact-head validation evidence.
- If the repo root cannot be inferred automatically, set `AGENT_TASK_MANAGER_REPO_ROOT`.
