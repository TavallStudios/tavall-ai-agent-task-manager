#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GEMINI_API_KEY:-}" && -n "${GOOGLE_API_KEY:-}" ]]; then
  GEMINI_API_KEY="$GOOGLE_API_KEY"
fi
: "${GEMINI_API_KEY:?GEMINI_API_KEY must be provided by /etc/tavall/graphiti-memory-plane.env}"
export GOOGLE_API_KEY="$GEMINI_API_KEY"
unset GEMINI_API_KEY
cd /srv/AgentTaskManager/sidecars/graphiti/mcp_server
exec /srv/AgentTaskManager/sidecars/graphiti/mcp_server/.venv/bin/python main.py \
  --config /srv/AgentTaskManager/sidecars/graphiti-config.yaml \
  --llm-provider gemini \
  --embedder-provider gemini \
  --database-provider falkordb \
  --model gemini-3.6-flash \
  --small-model gemini-3.5-flash-lite \
  --embedder-model gemini-embedding-001 \
  --group-id tavall \
  --host 127.0.0.1 \
  --port 8000
