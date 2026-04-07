#!/usr/bin/env bash
set -euo pipefail

REDIS_URL="${REDIS_URL:-${REDIS_MCP_URL:-${1:-}}}"

if [[ -z "$REDIS_URL" ]]; then
  echo "Missing REDIS_URL." >&2
  exit 1
fi

if command -v uvx >/dev/null 2>&1; then
  exec uvx redis-mcp-server --url "$REDIS_URL"
fi

if command -v redis-mcp-server >/dev/null 2>&1; then
  exec redis-mcp-server --url "$REDIS_URL"
fi

echo "redis-mcp-server not found. Install via 'uvx redis-mcp-server' or ensure it is on PATH." >&2
exit 1
