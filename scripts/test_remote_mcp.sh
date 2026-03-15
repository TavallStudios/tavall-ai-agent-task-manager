#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-${AGENT_TASK_MANAGER_MCP_BASE_URL:-https://docs.tavall.org/agent-task-manager}}"
USERNAME="${2:-${AGENT_TASK_MANAGER_USERNAME:-agent}}"
PASSWORD="${3:-${AGENT_TASK_MANAGER_PASSWORD:-}}"

if [[ -z "${PASSWORD}" ]]; then
  echo "AGENT_TASK_MANAGER_PASSWORD or the third argument is required" >&2
  exit 1
fi

endpoint="${BASE_URL%/}/mcp"
auth="${USERNAME}:${PASSWORD}"

init_body='{"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"remote-smoke","version":"0.1.0"}}}'
initialized_body='{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}'
tools_body='{"jsonrpc":"2.0","id":"tools-1","method":"tools/list","params":{}}'
summary_body='{"jsonrpc":"2.0","id":"call-1","method":"tools/call","params":{"name":"loadDashboardSummary","arguments":{}}}'

tmpdir="$(mktemp -d)"
trap 'rm -rf "${tmpdir}"' EXIT

curl -sS -D "${tmpdir}/init.headers" -o "${tmpdir}/init.json" \
  -u "${auth}" \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Content-Type: application/json' \
  -X POST "${endpoint}" \
  --data "${init_body}"

session_id="$(awk 'BEGIN{IGNORECASE=1}/^mcp-session-id:/{print $2}' "${tmpdir}/init.headers" | tr -d '\r')"

if [[ -z "${session_id}" ]]; then
  echo "Failed to obtain MCP session id" >&2
  cat "${tmpdir}/init.headers" >&2
  cat "${tmpdir}/init.json" >&2
  exit 1
fi

curl -sS -o /dev/null \
  -u "${auth}" \
  -H "Mcp-Session-Id: ${session_id}" \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Content-Type: application/json' \
  -X POST "${endpoint}" \
  --data "${initialized_body}"

curl -sS -o "${tmpdir}/tools.sse" \
  -u "${auth}" \
  -H "Mcp-Session-Id: ${session_id}" \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Content-Type: application/json' \
  -X POST "${endpoint}" \
  --data "${tools_body}"

curl -sS -o "${tmpdir}/summary.sse" \
  -u "${auth}" \
  -H "Mcp-Session-Id: ${session_id}" \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Content-Type: application/json' \
  -X POST "${endpoint}" \
  --data "${summary_body}"

echo "MCP session: ${session_id}"
echo
echo "Initialize response:"
cat "${tmpdir}/init.json"
echo
echo
echo "Tools list event:"
cat "${tmpdir}/tools.sse"
echo
echo
echo "Dashboard summary event:"
cat "${tmpdir}/summary.sse"
