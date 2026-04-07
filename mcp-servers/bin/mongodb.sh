#!/usr/bin/env bash
set -euo pipefail

MDB_MCP_CONNECTION_STRING="${MDB_MCP_CONNECTION_STRING:-${MONGODB_URI:-${MONGO_URL:-${1:-}}}}"

if [[ -z "$MDB_MCP_CONNECTION_STRING" ]]; then
  echo "Missing MDB_MCP_CONNECTION_STRING or MONGODB_URI." >&2
  exit 1
fi

export MDB_MCP_CONNECTION_STRING
exec npx -y mongodb-mcp-server
