#!/usr/bin/env bash
set -euo pipefail

POSTGRES_URL="${POSTGRES_URL:-${DATABASE_URL:-${PG_URL:-${1:-}}}}"

if [[ -z "$POSTGRES_URL" ]]; then
  echo "Missing POSTGRES_URL or DATABASE_URL." >&2
  exit 1
fi

exec npx -y @modelcontextprotocol/server-postgres "$POSTGRES_URL"
