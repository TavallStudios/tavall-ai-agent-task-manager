#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/task_store_env.sh"

namespace="$(atm_task_namespace)"
key="${namespace}:multi_agent:enabled"
mode="${1:-status}"

case "$mode" in
  on|enable|enabled|1|true)
    atm_redis_cli SET "$key" "1" >/dev/null
    printf '%s\n' "multi_agent_enabled=1"
    ;;
  off|disable|disabled|0|false)
    atm_redis_cli SET "$key" "0" >/dev/null
    printf '%s\n' "multi_agent_enabled=0"
    ;;
  status)
    current="$(atm_redis_cli GET "$key" 2>/dev/null || true)"
    if [[ -z "$current" ]]; then
      current="0"
    fi
    printf 'multi_agent_enabled=%s\n' "$current"
    ;;
  *)
    printf '%s\n' "Usage: set_multi_agent_mode.sh [on|off|status]" >&2
    exit 1
    ;;
esac
