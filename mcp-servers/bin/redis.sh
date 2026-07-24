#!/usr/bin/env bash
set -euo pipefail

REDIS_URL="${REDIS_URL:-${REDIS_MCP_URL:-${1:-}}}"

if [[ -z "$REDIS_URL" ]]; then
  echo "Missing REDIS_URL." >&2
  exit 1
fi

if command -v uvx >/dev/null 2>&1; then
  exec uvx --from redis-mcp-server@latest redis-mcp-server --url "$REDIS_URL"
fi

echo "uvx not found. Install uv and retry (uvx provides redis-mcp-server)." >&2
exit 1
