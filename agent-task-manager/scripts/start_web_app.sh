#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/web_app_env.sh"

detach=false
declare -a app_args=()
while (($# > 0)); do
  case "$1" in
    --detach)
      detach=true
      shift
      ;;
    *)
      app_args+=("$1")
      shift
      ;;
  esac
done

REPO_ROOT="$(atm_resolve_repo_root)"
LOG_FILE="$(atm_web_app_log_file)"
GRADLE_COMMAND="$(atm_resolve_gradle_command "$REPO_ROOT")"
"$GRADLE_COMMAND" --no-daemon --max-workers=1 stageDistribution
APP_CLASSPATH="$REPO_ROOT/distribution/agent-task-manager/application.jar:$REPO_ROOT/distribution/agent-task-manager/libs/*"

if [[ "$detach" == "true" ]]; then
  mkdir -p "$(dirname -- "$LOG_FILE")"
  (
    cd "$REPO_ROOT"
    exec java --enable-preview -cp "$APP_CLASSPATH" org.tavall.ai.app.AgentTaskManagerLauncher "${app_args[@]}"
  ) >>"$LOG_FILE" 2>&1 &
  disown || true
  printf '%s\n' "Starting AgentTaskManager web panel from $REPO_ROOT. Log: $LOG_FILE"
  exit 0
fi

cd "$REPO_ROOT"
exec java --enable-preview -cp "$APP_CLASSPATH" org.tavall.ai.app.AgentTaskManagerLauncher "${app_args[@]}"
