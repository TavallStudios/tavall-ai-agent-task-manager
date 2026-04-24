#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/task_store_env.sh"

task_id=""
project_key=""
agent_id=""
status=""
summary=""
checkpoint_kind="progress"
source_repo=""
details_json="{}"
owner_agent_id=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --task-id) task_id="$2"; shift 2 ;;
    --project) project_key="$2"; shift 2 ;;
    --agent-id) agent_id="$2"; shift 2 ;;
    --status) status="$2"; shift 2 ;;
    --summary) summary="$2"; shift 2 ;;
    --kind) checkpoint_kind="$2"; shift 2 ;;
    --source-repo) source_repo="$2"; shift 2 ;;
    --details-json) details_json="$2"; shift 2 ;;
    --owner-agent) owner_agent_id="$2"; shift 2 ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$task_id" || -z "$project_key" || -z "$agent_id" || -z "$status" || -z "$summary" ]]; then
  printf '%s\n' "Usage: record_checkpoint.sh --task-id ID --project KEY --agent-id ID --status STATUS --summary TEXT [--kind KIND] [--source-repo PATH] [--details-json JSON] [--owner-agent ID]" >&2
  exit 1
fi

now_utc="$(atm_now_utc)"
namespace="$(atm_task_namespace)"
checkpoint_key="${namespace}:checkpoint:${task_id}:${agent_id}"

if atm_multi_agent_enabled; then
  multi_agent_enabled="true"
else
  multi_agent_enabled="false"
fi

atm_redis_cli HSET "$checkpoint_key" \
  task_id "$task_id" \
  project_key "$project_key" \
  agent_id "$agent_id" \
  status "$status" \
  summary "$summary" \
  checkpoint_kind "$checkpoint_kind" \
  source_repo "$source_repo" \
  updated_at "$now_utc" >/dev/null

atm_redis_cli EXPIRE "$checkpoint_key" "${AGENT_TASK_MANAGER_CHECKPOINT_TTL_SECONDS:-172800}" >/dev/null
atm_redis_cli SADD "${namespace}:active" "$task_id" >/dev/null
atm_redis_cli ZADD "${namespace}:checkpoints" "$(date +%s)" "$checkpoint_key" >/dev/null

atm_psql -v ON_ERROR_STOP=1 \
  -v task_id="$task_id" \
  -v project_key="$project_key" \
  -v title="$summary" \
  -v status="$status" \
  -v owner_agent_id="$owner_agent_id" \
  -v source_repo="$source_repo" \
  -v checkpoint_kind="$checkpoint_kind" \
  -v agent_id="$agent_id" \
  -v summary="$summary" \
  -v details_json="$details_json" \
  -v multi_agent_enabled="$multi_agent_enabled" <<'SQL'
INSERT INTO agent_task_manager.agent_tasks (
  task_id,
  project_key,
  source_repo,
  task_kind,
  title,
  status,
  owner_agent_id,
  multi_agent_enabled
) VALUES (
  :'task_id',
  :'project_key',
  NULLIF(:'source_repo', ''),
  'general',
  :'title',
  :'status',
  NULLIF(:'owner_agent_id', ''),
  CAST(:'multi_agent_enabled' AS boolean)
)
ON CONFLICT (task_id) DO UPDATE SET
  project_key = EXCLUDED.project_key,
  source_repo = COALESCE(EXCLUDED.source_repo, agent_task_manager.agent_tasks.source_repo),
  status = EXCLUDED.status,
  owner_agent_id = COALESCE(EXCLUDED.owner_agent_id, agent_task_manager.agent_tasks.owner_agent_id),
  multi_agent_enabled = EXCLUDED.multi_agent_enabled;

INSERT INTO agent_task_manager.task_checkpoints (
  task_id,
  agent_id,
  checkpoint_kind,
  status,
  summary,
  details
) VALUES (
  :'task_id',
  :'agent_id',
  :'checkpoint_kind',
  :'status',
  :'summary',
  CAST(:'details_json' AS jsonb)
);
SQL

printf 'Recorded checkpoint for task %s (%s)\n' "$task_id" "$agent_id"
