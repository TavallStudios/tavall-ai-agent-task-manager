---
name: tavall-staging-reconciliation
description: Repair Tavall staging/feature PR topology while preserving useful stacks, active ownership, and unrelated parallel work.
---

# Tavall Staging Reconciliation

Use this shared skill from the reconciliation agent, or from orchestration when it is coordinating a bounded topology repair.

## Canonical functions

Read first:

- `repository_staging_discover`
- `repository_staging_inspect_graph`
- `repository_staging_resolve_base`
- `repository_staging_validate`

Topology mutation, only when authorized:

- `repository_staging_ensure`
- `repository_staging_attach`
- `repository_staging_set_state`

Environment evidence functions:

- `cloud_dev_lane_inspect`
- `cloud_dev_environment_resolve`
- `cloud_dev_environment_inspect`
- `cloud_dev_environment_components`
- `cloud_dev_environment_validations`

## Repair rules

1. Inspect the complete relevant open-PR graph before mutation.
2. Treat malformed metadata, duplicate active roots, ancestry cycles, direct-to-main feature work with active repository staging, stale/superseded roots, and wrong-base independent work as topology findings.
3. Preserve an existing feature stack. `repository_staging_attach` is for an independent PR or **stack root**; never use it to flatten descendants away from their true feature parent.
4. Prefer the closest meaningful staging boundary. Nested/domain staging is valid when it represents real ownership/testing/review scope.
5. Resolve the exact source snapshot for every participating repository and compare it with the environment generation's digest.
6. If a head or parent changed, create/reuse only the matching immutable environment generation; do not relabel prior validation or mutate a running historical generation.
7. Use `repository_staging_ensure` only after discovery proves the required root does not already exist in a usable state.
8. Use `repository_staging_set_state` for explicit ACTIVE/FROZEN/PROMOTING/SUPERSEDED transitions; never encode state by prose alone.
9. Re-run `repository_staging_validate`, inspect environment components, and bind local CI/review evidence to the resulting exact heads and source digest.
10. Keep unrelated work moving. Reconciliation is not a global PR-creation freeze.

Do not equate topology repair with production promotion, deployment, or approval of the code inside the repaired PRs.
