#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAR_PATH="${REPO_ROOT}/agent-task-manager-app/target/agent-task-manager-app-0.1.0-SNAPSHOT.jar"

if [[ ! -f "${JAR_PATH}" ]]; then
  mvn -q -pl agent-task-manager-app -am package
fi

exec java -jar "${JAR_PATH}" serve-mcp-stdio "$@"
