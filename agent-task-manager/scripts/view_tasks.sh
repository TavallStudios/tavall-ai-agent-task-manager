#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/task_store_env.sh"

limit="${1:-25}"

atm_psql -v ON_ERROR_STOP=1 -P pager=off -v task_limit="$limit" <<'SQL'
SELECT
  task_id,
  project_key,
  status,
  priority,
  multi_agent_enabled,
  COALESCE(active_lease_agent_id, '-') AS lease_agent,
  COALESCE(latest_checkpoint_agent_id, '-') AS checkpoint_agent,
  COALESCE(latest_checkpoint_summary, '-') AS latest_checkpoint_summary,
  updated_at
FROM agent_task_manager.task_overview
ORDER BY updated_at DESC, priority ASC
LIMIT CAST(:'task_limit' AS integer);
SQL
