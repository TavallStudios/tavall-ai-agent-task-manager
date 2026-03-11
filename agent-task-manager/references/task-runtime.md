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
- key namespace: `agent-task-manager:tasks:*`

Current keys:

- `agent-task-manager:tasks:multi_agent:enabled`
  - global feature flag
  - `1` means multi-agent tasking is enabled
  - `0` means tasks should stay single-agent by default
- `agent-task-manager:tasks:active`
  - set of active task ids
- `agent-task-manager:tasks:checkpoints`
  - sorted set of checkpoint keys by unix timestamp
- `agent-task-manager:tasks:checkpoint:{task_id}:{agent_id}`
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

- [bootstrap_task_store.sh](/srv/AgentTaskManager/agent-task-manager/scripts/bootstrap_task_store.sh)
- [task_store.sql](/srv/AgentTaskManager/agent-task-manager/scripts/sql/task_store.sql)

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

- [set_multi_agent_mode.sh](/srv/AgentTaskManager/agent-task-manager/scripts/set_multi_agent_mode.sh)
  - enables, disables, or inspects the global multi-agent flag
- [upsert_task.sh](/srv/AgentTaskManager/agent-task-manager/scripts/upsert_task.sh)
  - creates or updates a durable task row
- [record_checkpoint.sh](/srv/AgentTaskManager/agent-task-manager/scripts/record_checkpoint.sh)
  - writes an ephemeral checkpoint to Redis and a durable checkpoint to Postgres
- [view_tasks.sh](/srv/AgentTaskManager/agent-task-manager/scripts/view_tasks.sh)
  - reads the current durable task overview from Postgres
- [start_web_app.sh](/srv/AgentTaskManager/agent-task-manager/scripts/start_web_app.sh)
  - starts the Spring Boot control plane for phones, tablets, laptops, or remote browsers

## Recommended Flow

1. Bootstrap the schema once.
2. Enable multi-agent mode only when a task actually benefits from fan-out.
3. Upsert the task into Postgres before handing it to more than one agent.
4. Write frequent progress checkpoints to Redis and durable checkpoints to Postgres.
5. Use the Postgres view as the global viewer, not memory or raw chat logs.

## Web Control Plane

The runtime now includes an authenticated Spring Boot control plane:

- entrypoint: [start_web_app.sh](/srv/AgentTaskManager/agent-task-manager/scripts/start_web_app.sh)
- Maven project: [/srv/AgentTaskManager/pom.xml](/srv/AgentTaskManager/pom.xml)

Default bind:

- host: `0.0.0.0`
- port: `9000`

Current UI and API surface:

- `GET /login`
- `GET /`
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

## Why This Split

- `memory` stays clean and durable.
- `redis` stays fast and disposable.
- `postgres` becomes the viewer and history surface.
- agents can coordinate without dragging large chat transcripts into context.
- the mobile web app becomes the operator surface while agent bridges consume the same durable queue later.
