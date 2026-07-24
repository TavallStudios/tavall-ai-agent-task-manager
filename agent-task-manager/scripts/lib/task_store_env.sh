#!/usr/bin/env bash
set -euo pipefail

atm_urlencode() {
  local input="${1:-}"
  local output=""
  local index char
  for (( index=0; index<${#input}; index++ )); do
    char="${input:index:1}"
    case "$char" in
      [a-zA-Z0-9.~_-])
        output+="$char"
        ;;
      *)
        printf -v output '%s%%%02X' "$output" "'$char"
        ;;
    esac
  done
  printf '%s' "$output"
}

atm_default_task_db_url() {
  local env_file jdbc_url username password db_url
  for env_file in /etc/tavallcouriers.env /etc/tavall/tavall.env; do
    if [[ ! -f "$env_file" ]]; then
      continue
    fi

    unset DB_URL DB_USER DB_PASS NOVUS_POSTGRES_URL NOVUS_POSTGRES_USER NOVUS_POSTGRES_PASSWORD
    # shellcheck disable=SC1090
    source <(sudo -n cat "$env_file")

    jdbc_url="${NOVUS_POSTGRES_URL:-${DB_URL:-}}"
    username="${NOVUS_POSTGRES_USER:-${DB_USER:-}}"
    password="${NOVUS_POSTGRES_PASSWORD:-${DB_PASS:-}}"

    if [[ -z "$jdbc_url" ]]; then
      continue
    fi

    db_url="${jdbc_url#jdbc:}"
    if [[ -n "$username" && -n "$password" && "$db_url" != *"@"* ]]; then
      db_url="${db_url#postgresql://}"
      db_url="postgresql://${username}:$(atm_urlencode "$password")@${db_url}"
    fi

    printf '%s' "$db_url"
    return 0
  done

  return 1
}

atm_task_db_url() {
  local db_url="${AGENT_TASK_MANAGER_DB_URL:-}"
  if [[ -z "$db_url" ]]; then
    db_url="$(atm_default_task_db_url || true)"
  fi
  if [[ -z "$db_url" ]]; then
    printf '%s\n' "Could not determine AGENT_TASK_MANAGER_DB_URL." >&2
    return 1
  fi
  printf '%s' "$db_url"
}

atm_psql() {
  local db_url
  db_url="$(atm_task_db_url)"
  psql "$db_url" "$@"
}

atm_redis_cli() {
  redis-cli \
    -h "${AGENT_TASK_MANAGER_REDIS_HOST:-127.0.0.1}" \
    -p "${AGENT_TASK_MANAGER_REDIS_PORT:-6379}" \
    -n "${AGENT_TASK_MANAGER_REDIS_DB:-5}" \
    "$@"
}

atm_task_namespace() {
  printf '%s' "${AGENT_TASK_MANAGER_REDIS_NAMESPACE:-tavall-ai:tasks}"
}

atm_now_utc() {
  date -u +"%Y-%m-%dT%H:%M:%SZ"
}

atm_multi_agent_enabled() {
  local key raw normalized
  key="$(atm_task_namespace):multi_agent:enabled"
  raw="$(atm_redis_cli GET "$key" 2>/dev/null || true)"
  normalized="$(printf '%s' "$raw" | tr '[:upper:]' '[:lower:]')"
  [[ "$normalized" == "1" || "$normalized" == "true" || "$normalized" == "on" || "$normalized" == "enabled" ]]
}

