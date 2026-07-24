#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/sql/task_store.sql"

# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/task_store_env.sh"

atm_psql -v ON_ERROR_STOP=1 -f "$SQL_FILE"
printf '%s\n' "Applied agent task store schema from $SQL_FILE"
