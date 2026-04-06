#!/usr/bin/env bash
set -euo pipefail

atm_has_agent_task_manager_repo_layout() {
  local candidate="${1:-}"
  [[ -n "$candidate" ]] \
    && [[ -f "$candidate/pom.xml" ]] \
    && [[ -d "$candidate/tavall-ai-app" ]] \
    && [[ -d "$candidate/tavall-ai" ]]
}

atm_normalize_path() {
  local candidate="${1:-}"
  if [[ -z "$candidate" ]]; then
    printf '%s\n' ""
    return 0
  fi

  if [[ "$candidate" =~ ^[A-Za-z]:[\\/] ]] && command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$candidate"
    return 0
  fi

  printf '%s\n' "$candidate"
}

atm_append_candidate_if_present() {
  local -n candidate_list_ref=$1
  local candidate="${2:-}"
  candidate="$(atm_normalize_path "$candidate")"
  if [[ -n "$candidate" ]]; then
    candidate_list_ref+=("$candidate")
  fi
}

atm_append_parent_candidates() {
  local -n candidate_list_ref=$1
  local current="${2:-}"
  while [[ -n "$current" && "$current" != "/" && "$current" != "." ]]; do
    candidate_list_ref+=("$current")
    current="$(dirname -- "$current")"
  done
}

atm_resolve_repo_root() {
  local script_dir current drive root
  local -a candidates=()

  if [[ -n "${AGENT_TASK_MANAGER_REPO_ROOT:-}" ]]; then
    candidates+=("$AGENT_TASK_MANAGER_REPO_ROOT")
  fi

  script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
  atm_append_candidate_if_present candidates "$(cd -- "$script_dir/../../.." 2>/dev/null && pwd || true)"
  atm_append_parent_candidates candidates "${PWD:-}"

  if [[ -n "${HOME:-}" ]]; then
    atm_append_candidate_if_present candidates "$HOME/workspace/AgentTaskManager"
    atm_append_candidate_if_present candidates "$HOME/src/AgentTaskManager"
    atm_append_candidate_if_present candidates "$HOME/dev/AgentTaskManager"
  fi

  if [[ -n "${USERPROFILE:-}" ]]; then
    atm_append_candidate_if_present candidates "$USERPROFILE/workspace/AgentTaskManager"
    atm_append_candidate_if_present candidates "$USERPROFILE/src/AgentTaskManager"
    atm_append_candidate_if_present candidates "$USERPROFILE/dev/AgentTaskManager"
  fi

  atm_append_candidate_if_present candidates "/srv/AgentTaskManager"
  atm_append_candidate_if_present candidates "/workspace/AgentTaskManager"
  atm_append_candidate_if_present candidates "/workspaces/AgentTaskManager"

  for drive in /c /d /e /f /g /h /i /j /k /l /m /n /o /p /q /r /s /t /u /v /w /x /y /z; do
    atm_append_candidate_if_present candidates "$drive/workspace/AgentTaskManager"
    atm_append_candidate_if_present candidates "$drive/src/AgentTaskManager"
    atm_append_candidate_if_present candidates "$drive/dev/AgentTaskManager"
  done

  for root in "${candidates[@]}"; do
    if atm_has_agent_task_manager_repo_layout "$root"; then
      printf '%s\n' "$root"
      return 0
    fi
  done

  printf '%s\n' \
    "Could not resolve AgentTaskManager repo root. Set AGENT_TASK_MANAGER_REPO_ROOT to the checkout that contains pom.xml and tavall-ai-app." >&2
  return 1
}

atm_web_app_base_url() {
  printf '%s\n' "${AGENT_TASK_MANAGER_WEB_APP_URL:-http://127.0.0.1:9000}"
}

atm_web_app_probe_path() {
  printf '%s\n' "${AGENT_TASK_MANAGER_WEB_APP_PROBE_PATH:-/login}"
}

atm_web_app_probe_url() {
  printf '%s%s\n' "$(atm_web_app_base_url)" "$(atm_web_app_probe_path)"
}

atm_web_app_log_file() {
  local temp_root="${TMPDIR:-${TEMP:-${TMP:-/tmp}}}"
  local log_file
  temp_root="$(atm_normalize_path "$temp_root")"
  log_file="${AGENT_TASK_MANAGER_WEB_APP_LOG_FILE:-$temp_root/tavall-ai-web-app.log}"
  printf '%s\n' "$(atm_normalize_path "$log_file")"
}

atm_resolve_maven_command() {
  local repo_root="${1:-}"
  if [[ -x "$repo_root/mvnw" ]]; then
    printf '%s\n' "$repo_root/mvnw"
    return 0
  fi

  if command -v mvn >/dev/null 2>&1; then
    command -v mvn
    return 0
  fi

  if command -v mvn.cmd >/dev/null 2>&1; then
    command -v mvn.cmd
    return 0
  fi

  if command -v mvn.bat >/dev/null 2>&1; then
    command -v mvn.bat
    return 0
  fi

  printf '%s\n' \
    "Could not find mvn, mvn.cmd, mvn.bat, or an executable ./mvnw under $repo_root." >&2
  return 1
}

atm_http_status() {
  local url="${1:-}"
  if command -v curl >/dev/null 2>&1; then
    curl -k -L -sS -o /dev/null -w '%{http_code}' "$url"
    return 0
  fi

  if command -v python3 >/dev/null 2>&1; then
    python3 - "$url" <<'PY'
import sys
import urllib.error
import urllib.request

url = sys.argv[1]
request = urllib.request.Request(url, method="GET")
try:
    with urllib.request.urlopen(request, timeout=5) as response:
        print(response.getcode())
except urllib.error.HTTPError as exc:
    print(exc.code)
except Exception:
    sys.exit(1)
PY
    return $?
  fi

  if command -v python >/dev/null 2>&1; then
    python - "$url" <<'PY'
import sys
import urllib2

url = sys.argv[1]
request = urllib2.Request(url)
try:
    response = urllib2.urlopen(request, timeout=5)
    print(response.getcode())
except urllib2.HTTPError as exc:
    print(exc.code)
except Exception:
    sys.exit(1)
PY
    return $?
  fi

  printf '%s\n' "Neither curl nor python is available for web panel health checks." >&2
  return 1
}

atm_web_app_is_running() {
  local status
  status="$(atm_http_status "$(atm_web_app_probe_url)" 2>/dev/null || true)"
  [[ "$status" == "200" || "$status" == "302" || "$status" == "401" || "$status" == "403" ]]
}


