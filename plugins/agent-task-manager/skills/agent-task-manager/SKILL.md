---
name: agent-task-manager
description: Manage the AgentTaskManager plugin-managed cross-project harness and task runtime. Use when Codex needs the installed AgentTaskManager plugin to own local MCP registration, optional operator-surface startup, repo-context or worker-context MCP usage, Java validation and approval flow, deterministic git workflow through `planGitCommit` / `prepareGitBranch` / `createGitCommit`, or shared Redis/Postgres task coordination across repos.
---

# Agent Task Manager

Use this plugin-local skill when the AgentTaskManager plugin is installed.

## Runtime Ownership

1. Let the plugin-local `.mcp.json` own local AgentTaskManager MCP registration.
2. Prefer the plugin helper `scripts/ensure_operator_surface.py` only when a local HTTP operator surface is actually needed.
3. If the plugin-managed runtime is unavailable, fall back to the repo-local launchers and remote `/mcp` flow described in [references/task-runtime.md](references/task-runtime.md).

## Workflow

1. Call `runHarnessToolBundle(repo-context)` first.
2. Call `runHarnessToolBundle(worker-context)` when worker or task state matters.
3. Use [references/tool-surface.md](references/tool-surface.md) for retrieval, artifact, approval, and validation tool selection.
4. For Java work, call `loadCleanJavaTaskContext`, then `runHarnessToolBundle(java-context)`, then `runCleanJavaHarness`.
5. For repository mutation, follow [references/git-workflow.md](references/git-workflow.md) and use `planGitCommit`, then `prepareGitBranch`, then `createGitCommit`.

## Fallback Policy

- Prefer the plugin-managed `agent-task-manager` MCP server before direct per-tool MCP injection.
- Let repo-context inspection prefer remote brokering first, then local downstream MCP, and only then controlled local fallback.
- Treat direct shell or ripgrep or file probing as fallback-only when harness tools are unavailable or failing.
- Do not use raw shell git mutation as the primary path.

## Notes

- This plugin owns runtime registration and startup helpers. The skill owns workflow and policy.
- Keep commits concern-scoped and use verbose commit bodies through the first-party MCP git workflow.
- If the repo root cannot be inferred automatically, set `AGENT_TASK_MANAGER_REPO_ROOT`.
