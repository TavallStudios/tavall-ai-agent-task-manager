#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/web_app_env.sh"

detach=false
declare -a mvn_args=()
while (($# > 0)); do
  case "$1" in
    --detach)
      detach=true
      shift
      ;;
    *)
      mvn_args+=("$1")
      shift
      ;;
  esac
done

REPO_ROOT="$(atm_resolve_repo_root)"
LOG_FILE="$(atm_web_app_log_file)"
MAVEN_COMMAND="$(atm_resolve_maven_command "$REPO_ROOT")"

if [[ "$detach" == "true" ]]; then
  mkdir -p "$(dirname -- "$LOG_FILE")"
  (
    cd "$REPO_ROOT"
    exec "$MAVEN_COMMAND" -pl agent-task-manager-app -am spring-boot:run "${mvn_args[@]}"
  ) >>"$LOG_FILE" 2>&1 &
  disown || true
  printf '%s\n' "Starting AgentTaskManager web panel from $REPO_ROOT. Log: $LOG_FILE"
  exit 0
fi

cd "$REPO_ROOT"
exec "$MAVEN_COMMAND" -pl agent-task-manager-app -am spring-boot:run "${mvn_args[@]}"
