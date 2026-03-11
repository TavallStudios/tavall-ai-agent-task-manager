#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/task_store_env.sh"

task_id=""
project_key=""
title=""
status=""
priority="100"
task_kind="general"
owner_agent_id=""
source_repo=""
payload_json="{}"
due_at=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --task-id) task_id="$2"; shift 2 ;;
    --project) project_key="$2"; shift 2 ;;
    --title) title="$2"; shift 2 ;;
    --status) status="$2"; shift 2 ;;
    --priority) priority="$2"; shift 2 ;;
    --kind) task_kind="$2"; shift 2 ;;
    --owner-agent) owner_agent_id="$2"; shift 2 ;;
    --source-repo) source_repo="$2"; shift 2 ;;
    --payload-json) payload_json="$2"; shift 2 ;;
    --due-at) due_at="$2"; shift 2 ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$task_id" || -z "$project_key" || -z "$title" || -z "$status" ]]; then
  printf '%s\n' "Usage: upsert_task.sh --task-id ID --project KEY --title TITLE --status STATUS [--priority N] [--kind KIND] [--owner-agent ID] [--source-repo PATH] [--payload-json JSON] [--due-at TIMESTAMPTZ]" >&2
  exit 1
fi

if atm_multi_agent_enabled; then
  multi_agent_enabled="true"
else
  multi_agent_enabled="false"
fi

atm_psql -v ON_ERROR_STOP=1 \
  -v task_id="$task_id" \
  -v project_key="$project_key" \
  -v title="$title" \
  -v status="$status" \
  -v priority="$priority" \
  -v task_kind="$task_kind" \
  -v owner_agent_id="$owner_agent_id" \
  -v source_repo="$source_repo" \
  -v payload_json="$payload_json" \
  -v due_at="$due_at" \
  -v multi_agent_enabled="$multi_agent_enabled" <<'SQL'
INSERT INTO agent_task_manager.agent_tasks (
  task_id,
  project_key,
  source_repo,
  task_kind,
  title,
  status,
  priority,
  owner_agent_id,
  multi_agent_enabled,
  payload,
  due_at
) VALUES (
  :'task_id',
  :'project_key',
  NULLIF(:'source_repo', ''),
  :'task_kind',
  :'title',
  :'status',
  CAST(:'priority' AS integer),
  NULLIF(:'owner_agent_id', ''),
  CAST(:'multi_agent_enabled' AS boolean),
  CAST(:'payload_json' AS jsonb),
  CAST(NULLIF(:'due_at', '') AS timestamptz)
)
ON CONFLICT (task_id) DO UPDATE SET
  project_key = EXCLUDED.project_key,
  source_repo = EXCLUDED.source_repo,
  task_kind = EXCLUDED.task_kind,
  title = EXCLUDED.title,
  status = EXCLUDED.status,
  priority = EXCLUDED.priority,
  owner_agent_id = EXCLUDED.owner_agent_id,
  multi_agent_enabled = EXCLUDED.multi_agent_enabled,
  payload = EXCLUDED.payload,
  due_at = EXCLUDED.due_at;
SQL

printf 'Upserted task %s\n' "$task_id"
