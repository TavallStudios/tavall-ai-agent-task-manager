#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/web_app_env.sh"

if atm_web_app_is_running; then
  printf '%s\n' "AgentTaskManager web panel is already reachable at $(atm_web_app_probe_url)."
  exit 0
fi

if ! "$SCRIPT_DIR/start_web_app.sh" --detach "$@"; then
  printf '%s\n' "AgentTaskManager web panel start attempt failed. Continuing without the panel." >&2
  exit 1
fi

timeout_seconds="${AGENT_TASK_MANAGER_WEB_APP_START_TIMEOUT_SECONDS:-20}"
elapsed=0
while (( elapsed < timeout_seconds )); do
  if atm_web_app_is_running; then
    printf '%s\n' "AgentTaskManager web panel is reachable at $(atm_web_app_probe_url)."
    exit 0
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

printf '%s\n' \
  "AgentTaskManager web panel did not become reachable within ${timeout_seconds}s. Continuing without the panel." >&2
exit 1
