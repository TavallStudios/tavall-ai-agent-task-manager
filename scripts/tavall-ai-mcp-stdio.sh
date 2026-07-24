#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DISTRIBUTION_PATH="${REPO_ROOT}/distribution/agent-task-manager"

if [[ ! -f "${DISTRIBUTION_PATH}/application.jar" || ! -d "${DISTRIBUTION_PATH}/libs" ]]; then
  "${REPO_ROOT}/gradlew" --no-daemon --max-workers=1 stageDistribution
fi

if [[ ! -f "${DISTRIBUTION_PATH}/application.jar" || ! -d "${DISTRIBUTION_PATH}/libs" ]]; then
  echo "Failed to prepare the AgentTaskManager distribution." >&2
  exit 1
fi

PYTHON_BIN="${PYTHON_BIN:-}"
if [[ -z "${PYTHON_BIN}" ]]; then
  if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
  else
    PYTHON_BIN="python"
  fi
fi

BRIDGE_SCRIPT="${REPO_ROOT}/scripts/mcp_stdio_json_bridge.py"
BRIDGE_ARGS=("--cwd" "${REPO_ROOT}" "--distribution-path" "${DISTRIBUTION_PATH}")

if [[ -n "${TAVALL_AI_STDIO_PROTOCOL:-}" ]]; then
  BRIDGE_ARGS+=("--protocol" "${TAVALL_AI_STDIO_PROTOCOL}")
fi

if [[ -n "${TAVALL_AI_STDIO_DISABLE_DB:-}" ]]; then
  BRIDGE_ARGS+=("--disable-db")
fi

exec "${PYTHON_BIN}" "${BRIDGE_SCRIPT}" "${BRIDGE_ARGS[@]}" -- "$@"
