---
name: agent-task-manager
description: Manage the AgentTaskManager cross-project harness and task runtime. Use when Codex needs to work through AgentTaskManager for repository inspection or editing across repos, worker or task coordination, repo-context or worker-context MCP usage, Java validation and approval flow, deterministic git workflow through `planGitCommit` / `prepareGitBranch` / `createGitCommit`, or when maintaining the shared Redis/Postgres task runtime and checkpoints.
---

# Agent Task Manager

Use this skill as the canonical AgentTaskManager harness and task-runtime workflow across repos.

## Operator Surface

1. If the AgentTaskManager plugin is installed, let it own local MCP registration and runtime startup first.
2. If remote HTTP MCP is expected, probe `/mcp` first and only then fall back to repo-local stdio launchers.
3. Only use a local HTTP operator surface when the task actually needs it. The runtime is still MCP-first.

## Quick Start

1. Read [references/task-runtime.md](references/task-runtime.md) if you need the Redis key contract, Postgres schema, or the intended task flow.
2. Run `scripts/bootstrap_task_store.sh` once before first use on a machine.
3. Use `scripts/set_multi_agent_mode.sh on|off|status` to control whether tasks should fan out to multiple agents.
4. Use `scripts/upsert_task.sh` to create or update the durable task row in Postgres.
5. Use `scripts/record_checkpoint.sh` during work to write a fresh Redis checkpoint and a durable Postgres checkpoint.
6. Use `scripts/view_tasks.sh` to inspect the current viewer state.
7. Prefer the plugin-managed runtime when available, then fall back to the repo-local MCP stdio launchers or the remote `/mcp` endpoint.

## Harness Workflow

Read [references/harness-workflow.md](references/harness-workflow.md) for the full repo-edit flow.
Use this default order when the harness is active:

1. Ensure the central MCP surface is available first through the installed AgentTaskManager plugin, then the repo-local stdio launcher, then a remote `/mcp` endpoint.
2. Call `runHarnessToolBundle(repo-context)` first for repository inspection and retrieval.
3. Call `runHarnessToolBundle(worker-context)` when worker or task runtime state matters.
4. Use [references/tool-surface.md](references/tool-surface.md) for retrieval, artifact, approval, and validation tool selection.
5. For Java implementation work, call `loadCleanJavaTaskContext`, then `runHarnessToolBundle(java-context)`, then `runCleanJavaHarness`.
6. For branch and commit mutation, follow [references/git-workflow.md](references/git-workflow.md) and use `planGitCommit`, then `prepareGitBranch`, then `createGitCommit`.
7. Never use downstream generic git mutation tools such as `git_commit`, `git_add`, `git_checkout`, `git_create_branch`, or `git_reset` when the AgentTaskManager git workflow is available.

## Always-On Memory Flow

This skill should treat prompt-thread memory as an always-on stage, not an optional extra.

1. On every incoming prompt, ensure the chat has a stable `threadKey`.
   If the caller provides an explicit chat key or chat name, reuse it.
   Otherwise fall back to the repo-plus-target derived thread key.
2. Before execution, look up that `threadKey` in the durable prompt-thread store and fetch thread-scoped semantic memory.
3. Use thread-scoped memory first, then broader project memory, then knowledge memory when preparing the execution prompt.
4. Persist the accepted MCP interaction to durable thread memory as soon as dispatch begins.
5. Persist the memory-lookup summary, final response or failure, and a thread snapshot after the run completes or fails.
6. Prefer the new prompt-thread search surfaces when you need historical chat recall:
   - MCP: `searchPromptThreads`, `searchPromptThreadMemory`

Recommended memory stages:

- Stage 1: inbound MCP interaction accepted
  Resolve `threadKey` and store the accepted interaction.
- Stage 2: pre-dispatch lookup
  Run exact `threadKey` lookup and thread-scoped semantic search, then inject thread and project memory into the execution envelope.
- Stage 3: in-flight persistence
  Store the lookup event and interaction linkage so the remote store is current.
- Stage 4: final response
  Store the assistant answer plus any durable recovery note or summary.
- Stage 5: thread snapshot
  Persist a compact snapshot of the latest thread so old chats remain searchable by key and by vector similarity.

## Fallback Policy

- Prefer the downstream central MCP server (`agent-task-manager`) instead of direct per-tool MCP process injection.
- Let repo-context inspection prefer remote brokering first, then local downstream MCP, and only then controlled local fallback.
- Treat direct shell or ripgrep or file probing as fallback-only when harness tools are unavailable or failing.
- Do not use raw shell git mutation as the primary path.
- Do not use downstream generic git mutation tools through the harness. Commit mutations must stay on `planGitCommit` -> `prepareGitBranch` -> `createGitCommit`.

## Approval Policy

- This skill assumes the runtime tool-policy gate is active and can fail the run with `exitCode=97`.
- Workers do not self-approve. Expect cleanup review, validation, and patch-scope gating before approval.
- Do not treat a patch as done when harness validation returns blocking findings.

## Task Model

- Redis is ephemeral.
  Store feature flags, recent checkpoints, leases, and short-lived coordination state there.
- Postgres is durable.
  Store task rows, checkpoint history, event history, and the viewer query surface there.
- Do not use long-term memory as the task registry.
  Memory is only for durable facts that matter across sessions, not mutable task state.

## Scripts

- `scripts/bootstrap_task_store.sh`
  Apply the shared Postgres schema from `scripts/sql/task_store.sql`.
- `scripts/set_multi_agent_mode.sh`
  Toggle the global multi-agent flag in Redis.
- `scripts/upsert_task.sh`
  Create or update a durable task row.
- `scripts/record_checkpoint.sh`
  Write a Redis checkpoint and append a durable Postgres checkpoint.
- `scripts/view_tasks.sh`
  Query the durable task overview.
- `scripts/agent-task-manager-mcp-stdio.sh`
  Starts the central MCP server over stdio on Unix-like hosts.
- `scripts/agent-task-manager-mcp-stdio.cmd`
  Starts the central MCP server over stdio on Windows.
- `scripts/test_remote_mcp.sh`
  Performs the streamable HTTP smoke test against the configured `/mcp` endpoint.

Repo-local plugin surface:

- `plugins/agent-task-manager/.mcp.json`
  Registers the local `agent-task-manager` MCP through the plugin-managed Python launcher.
- `plugins/agent-task-manager/scripts/start_agent_task_manager_mcp.py`
  Resolves the repo root, ensures the app jar exists, and starts the stdio MCP server.
- `plugins/agent-task-manager/scripts/ensure_operator_surface.py`
  Optionally starts the local HTTP operator surface when a browser-facing runtime is actually needed.

## Environment

The scripts are shell-based and can be used from any repo. They support overrides through environment variables:

- `AGENT_TASK_MANAGER_DB_URL`
- `AGENT_TASK_MANAGER_REDIS_HOST`
- `AGENT_TASK_MANAGER_REDIS_PORT`
- `AGENT_TASK_MANAGER_REDIS_DB`
- `AGENT_TASK_MANAGER_REDIS_NAMESPACE`
- `AGENT_TASK_MANAGER_CHECKPOINT_TTL_SECONDS`
- `AGENT_TASK_MANAGER_USERNAME`
- `AGENT_TASK_MANAGER_PASSWORD`
- `AGENT_TASK_MANAGER_BRIDGE_ENABLED`
- `AGENT_TASK_MANAGER_BRIDGE_AGENT_ID`
- `AGENT_TASK_MANAGER_BRIDGE_COMMAND`
- `AGENT_TASK_MANAGER_BRIDGE_POLL_INTERVAL_MS`
- `AGENT_TASK_MANAGER_BRIDGE_MAX_MESSAGE_CHARS`
- `AGENT_TASK_MANAGER_REPO_ROOT`
- `AGENT_TASK_MANAGER_CODEX_DOWNSTREAM_CENTRAL_SERVER`
- `AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED`
- `AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS`
- `AGENT_TASK_MANAGER_MCP_BASE_URL`
- `AGENT_TASK_MANAGER_MCP_ENDPOINT`

Recommended defaults for this skill:

- `AGENT_TASK_MANAGER_CODEX_DOWNSTREAM_CENTRAL_SERVER=agent-task-manager`
- `AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED=true`
- `AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS=`

If `AGENT_TASK_MANAGER_DB_URL` is not set, the helper script will try the local service env files already used on this machine.

## Notes

- Keep this skill repo generic. Do not add project-specific names, task ids, or schemas unless they are deliberately parameterized.
- Keep `SKILL.md` as navigation and use the bundled references for harness, tool-surface, and git workflow detail.
- Prefer shell or small deterministic scripts for task-runtime actions. For repository mutation through the harness, prefer the first-party MCP git workflow over local shell git commands.
- Treat any user request that says "git commit tool" as a request for the first-party git workflow, not the downstream generic `git_commit` connector tool.
- The installed skill copy does not contain the full Maven project. If the repo root cannot be inferred from the current checkout or common workspace paths, set `AGENT_TASK_MANAGER_REPO_ROOT`.
- The clean split is: plugin owns runtime registration and startup helpers; skill owns workflow, tool ordering, and policy.
- The stdio launcher scripts need either `mvn` on `PATH` or an executable `mvnw` in the resolved AgentTaskManager checkout when the jar is not already built.
- The runtime is MCP-first. Do not assume a local dashboard, a browser workflow, or queued prompt-request execution.
