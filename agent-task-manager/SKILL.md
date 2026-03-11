---
name: agent-task-manager
description: Manage a local-first cross-project task runtime for AI agents. Use when Codex needs to bootstrap or maintain the shared task store, enable or disable multi-agent work, create or update durable task rows, record task checkpoints, inspect task viewer state, or coordinate agent leases and progress across repositories using local Redis and Postgres.
---

# Agent Task Manager

Use the bundled scripts in this skill as the canonical task runtime for cross-project agent coordination.

## Quick Start

1. Read [references/task-runtime.md](references/task-runtime.md) if you need the Redis key contract, Postgres schema, or the intended task flow.
2. Run `scripts/bootstrap_task_store.sh` once before first use on a machine.
3. Use `scripts/set_multi_agent_mode.sh on|off|status` to control whether tasks should fan out to multiple agents.
4. Use `scripts/upsert_task.sh` to create or update the durable task row in Postgres.
5. Use `scripts/record_checkpoint.sh` during work to write a fresh Redis checkpoint and a durable Postgres checkpoint.
6. Use `scripts/view_tasks.sh` to inspect the current viewer state.
7. Use `scripts/start_web_app.sh` to launch the Spring Boot control plane on port `9000` for mobile and browser access.
8. Leave the web app running if queued prompts should execute automatically through the local Codex bridge.

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
- `scripts/start_web_app.sh`
  Start the Spring Boot web app that exposes the authenticated dashboard plus `/api/tasks`, `/api/prompt-requests`, and `/api/runtime/status`.

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

If `AGENT_TASK_MANAGER_DB_URL` is not set, the helper script will try the local service env files already used on this machine.

## Notes

- Keep this skill repo generic. Do not add project-specific names, task ids, or schemas unless they are deliberately parameterized.
- Prefer shell or small deterministic scripts for runtime actions. The Boot app is the shared viewer and prompt queue, while the shell scripts remain the lowest-friction runtime tools.
- The Boot app now also runs the local Codex bridge. Queued prompt requests move through `queued -> claimed -> running -> completed|failed` and their live output is persisted into `prompt_messages`.
