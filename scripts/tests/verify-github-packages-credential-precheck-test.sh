#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY="${ROOT}/scripts/ci/verify"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

[[ -x "${VERIFY}" ]] || fail "scripts/ci/verify must be executable"

set +e
output="$(env -u GITHUB_TOKEN bash "${VERIFY}" 2>&1)"
status=$?
set -e

[[ ${status} -eq 2 ]] || fail "missing package credentials must fail fast with status 2: ${output}"
[[ "${output}" == *"GITHUB_TOKEN is required for Tavall AI verification"* ]] \
  || fail "missing credential failure must be actionable: ${output}"
[[ "${output}" != *"Could not resolve"* ]] \
  || fail "credential preflight must fail before Gradle dependency-resolution fallout: ${output}"

printf 'PASS: Tavall AI verifier fails fast when package credentials are unavailable\n'
