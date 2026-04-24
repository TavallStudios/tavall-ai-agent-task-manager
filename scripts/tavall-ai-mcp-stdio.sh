#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DIR="${REPO_ROOT}/tavall-ai-app/target"

JAR_PATH=""
if [[ -d "${TARGET_DIR}" ]]; then
  JAR_PATH="$(ls -t "${TARGET_DIR}"/tavall-ai-app-*.jar 2>/dev/null | grep -Ev 'sources|javadoc|tests|plain|original' | head -n 1 || true)"
fi

if [[ -z "${JAR_PATH}" || ! -f "${JAR_PATH}" ]]; then
  mvn -q -pl tavall-ai-app -am -Dmaven.test.skip=true package
  JAR_PATH="$(ls -t "${TARGET_DIR}"/tavall-ai-app-*.jar 2>/dev/null | grep -Ev 'sources|javadoc|tests|plain|original' | head -n 1 || true)"
fi

if [[ -z "${JAR_PATH}" || ! -f "${JAR_PATH}" ]]; then
  echo "Failed to locate tavall-ai-app jar in ${TARGET_DIR}." >&2
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
BRIDGE_ARGS=("--cwd" "${REPO_ROOT}" "--jar-path" "${JAR_PATH}")

if [[ -n "${TAVALL_AI_STDIO_PROTOCOL:-}" ]]; then
  BRIDGE_ARGS+=("--protocol" "${TAVALL_AI_STDIO_PROTOCOL}")
fi

if [[ -n "${TAVALL_AI_STDIO_DISABLE_DB:-}" ]]; then
  BRIDGE_ARGS+=("--disable-db")
fi

exec "${PYTHON_BIN}" "${BRIDGE_SCRIPT}" "${BRIDGE_ARGS[@]}" -- "$@"
