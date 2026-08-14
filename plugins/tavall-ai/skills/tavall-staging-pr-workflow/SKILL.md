---
name: tavall-staging-pr-workflow
description: Resolve and preserve Tavall staging PR ancestry for substantive repository work before mutation, integration, review, or exact-head acceptance.
---

# Tavall Staging PR Workflow

Use this shared skill whenever work creates, changes, reviews, validates, or coordinates a Tavall pull request.

## Canonical read/decision functions

- `repository_staging_discover`
- `repository_staging_inspect_graph`
- `repository_staging_resolve_base`
- `repository_staging_validate`

These are Function Catalog functions. Requesting them from agent metadata does not grant repository authority; the execution-specific Function Catalog view and repository provider policy decide what is callable.

## Workflow

1. **Discover** staging roots and parse `tavall-staging:v1` metadata before selecting a base.
2. **Inspect the graph** when current open PR ancestry, nested/domain staging, or dependent work could matter.
3. **Classify the work** as independent, dependent on a feature PR, or explicitly part of an existing stack.
4. **Resolve the base** with `repository_staging_resolve_base` rather than defaulting to `main`.
5. **Preserve an existing feature parent** for dependent work. Do not flatten a child directly onto staging merely because staging exists.
6. **For independent work**, target the closest valid active staging ancestor appropriate to the repository/domain/release scope.
7. Implement/review/validate on the resulting exact head and push meaningful mutation checkpoints.
8. **Validate topology** with `repository_staging_validate` before claiming the PR/staging graph is healthy.

## Semantics

- A feature PR is focused change/review truth.
- A staging PR is combined future-tree and integration/validation truth.
- Merging a child into staging means integrated for combined validation. It does **not** mean production-ready.
- Promotion from a staging root to `main` is a separate boundary.
- Source promotion is separate from deployment.
- Staging ancestry does not grant Cloud, executable, credential, deployment, production-world, or Function Catalog authority.
- Do not invent a global PR count limit or freeze unrelated work during reconciliation.
- Preserve exact staging-head evidence because the combined staging head, not stale `main`, is the integration acceptance target.
