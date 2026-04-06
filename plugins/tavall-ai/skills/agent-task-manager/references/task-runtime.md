# Agent Task Runtime

This document defines the local-first task runtime for cross-project AI coordination.

## Goals

- keep mutable coordination state out of long-term memory
- make Postgres the durable task viewer and history store
- use Redis for fast leases, feature flags, and recent checkpoints
- keep the task model simple enough for shell scripts and MCP tools to share
- keep the runtime independent from any one project repo

## Redis Contract

Redis is the ephemeral coordination layer. Default connection:

- host: `127.0.0.1`
- port: `6379`
- db: `5`
- key namespace: `tavall-ai:tasks:*`

Current keys:

- `tavall-ai:tasks:multi_agent:enabled`
  - global feature flag
  - `1` means multi-agent tasking is enabled
  - `0` means tasks should stay single-agent by default
- `tavall-ai:tasks:active`
  - set of active task ids
- `tavall-ai:tasks:checkpoints`
  - sorted set of checkpoint keys by unix timestamp
- `tavall-ai:tasks:checkpoint:{task_id}:{agent_id}`
  - hash for the latest ephemeral checkpoint from one agent on one task
  - intended TTL: 48 hours by default

Redis should only hold:

- leases
- enable or disable flags
- in-progress checkpoints
- heartbeat-like status

Redis should not be treated as the durable viewer or audit log.

## Postgres Contract

Postgres is the durable source of truth for the task viewer. The schema is installed by:

- [bootstrap_task_store.sh](/srv/AgentTaskManager/tavall-ai/scripts/bootstrap_task_store.sh)
- [task_store.sql](/srv/AgentTaskManager/tavall-ai/scripts/sql/task_store.sql)

Schema name:

- `agent_task_manager`

Tables:

- `agent_tasks`
  - durable task row
  - current status, priority, project key, owner, and task metadata
- `task_checkpoints`
  - append-only checkpoint history
- `task_events`
  - append-only event history for future viewer timelines
- `agent_leases`
  - current active lease view if a task is being actively held
- `task_overview`
  - convenience view for the viewer and shell queries

## Shell Entry Points

- [set_multi_agent_mode.sh](/srv/AgentTaskManager/tavall-ai/scripts/set_multi_agent_mode.sh)
  - enables, disables, or inspects the global multi-agent flag
- [upsert_task.sh](/srv/AgentTaskManager/tavall-ai/scripts/upsert_task.sh)
  - creates or updates a durable task row
- [record_checkpoint.sh](/srv/AgentTaskManager/tavall-ai/scripts/record_checkpoint.sh)
  - writes an ephemeral checkpoint to Redis and a durable checkpoint to Postgres
- [view_tasks.sh](/srv/AgentTaskManager/tavall-ai/scripts/view_tasks.sh)
  - reads the current durable task overview from Postgres
- [tavall-ai-mcp-stdio.sh](/srv/AgentTaskManager/scripts/tavall-ai-mcp-stdio.sh)
  - starts the central MCP server over stdio on Unix-like hosts
- [tavall-ai-mcp-stdio.cmd](/srv/AgentTaskManager/scripts/tavall-ai-mcp-stdio.cmd)
  - starts the central MCP server over stdio on Windows

Plugin-owned runtime helpers:

- [start_agent_task_manager_mcp.py](/srv/AgentTaskManager/plugins/tavall-ai/scripts/start_agent_task_manager_mcp.py)
  - resolves the repo root, ensures the app jar exists, and starts the stdio MCP runtime
- [ensure_operator_surface.py](/srv/AgentTaskManager/plugins/tavall-ai/scripts/ensure_operator_surface.py)
  - optionally starts the local HTTP operator surface when a browser-facing runtime is actually needed

## Recommended Flow

1. Bootstrap the schema once.
2. Enable multi-agent mode only when a task actually benefits from fan-out.
3. Upsert the task into Postgres before handing it to more than one agent.
4. Write frequent progress checkpoints to Redis and durable checkpoints to Postgres.
5. Use the Postgres view as the global viewer, not memory or raw chat logs.

## MCP Runtime

The preferred runtime is MCP-first:

- preferred plugin entrypoint: [start_agent_task_manager_mcp.py](/srv/AgentTaskManager/plugins/tavall-ai/scripts/start_agent_task_manager_mcp.py)
- repo-local launchers:
  - [tavall-ai-mcp-stdio.sh](/srv/AgentTaskManager/scripts/tavall-ai-mcp-stdio.sh)
  - [tavall-ai-mcp-stdio.cmd](/srv/AgentTaskManager/scripts/tavall-ai-mcp-stdio.cmd)
- Maven project: [/srv/AgentTaskManager/pom.xml](/srv/AgentTaskManager/pom.xml)

When HTTP transport is needed:

- optional operator-surface helper: [ensure_operator_surface.py](/srv/AgentTaskManager/plugins/tavall-ai/scripts/ensure_operator_surface.py)
- default bind:
  - host: `0.0.0.0`
  - port: `9000`

Current HTTP surface:

- `GET /login`
- `GET /`
- `POST /mcp`
- `GET /api/runtime/status`
- `GET /api/tasks?limit=25&project=&status=`
- `GET /api/tasks/{taskId}`
- `GET /api/prompt-requests?limit=25&status=`
- `GET /api/prompt-requests/{requestId}`
- `POST /api/prompt-requests`

Auth:

- Spring Security form login
- username via `AGENT_TASK_MANAGER_USERNAME`
- password via `AGENT_TASK_MANAGER_PASSWORD`
- if the password is not set, the app generates one at startup and logs it once

Bridge behavior:

- the web app includes a local Codex bridge worker by default
- queued prompt requests are claimed from Postgres, executed with `codex exec`, and written back into:
  - `prompt_runs`
  - `prompt_messages`
  - `agent_sessions`
- `read-only` requests run with Codex read-only sandboxing
- `edit` and `run-tests` requests run with workspace-write sandboxing

Bridge env overrides:

- `AGENT_TASK_MANAGER_BRIDGE_ENABLED`
- `AGENT_TASK_MANAGER_BRIDGE_AGENT_ID`
- `AGENT_TASK_MANAGER_BRIDGE_COMMAND`
- `AGENT_TASK_MANAGER_BRIDGE_POLL_INTERVAL_MS`
- `AGENT_TASK_MANAGER_BRIDGE_MAX_MESSAGE_CHARS`

Web panel startup overrides:

- `AGENT_TASK_MANAGER_REPO_ROOT`
- `AGENT_TASK_MANAGER_MCP_BASE_URL`
- `AGENT_TASK_MANAGER_MCP_ENDPOINT`

## Why This Split

- `memory` stays clean and durable.
- `redis` stays fast and disposable.
- `postgres` becomes the viewer and history surface.
- agents can coordinate without dragging large chat transcripts into context.
- the mobile web app becomes the operator surface while agent bridges consume the same durable queue later.


