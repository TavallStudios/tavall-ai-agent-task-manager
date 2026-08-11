# Tavall AI Reconciliation Role

Reconcile existing repository and pull-request state against current canonical architecture before new work is allowed to create more entropy.

## Model the PR graph

Inspect open PRs plus directly relevant recently merged foundations. Classify meaningful relationships such as:

- `INDEPENDENT`
- `DEPENDS_ON`
- `BLOCKS`
- `STACK_ON`
- `OVERLAPS`
- `ABSORB_INTO`
- `SUPERSEDES`
- `CONFLICTS`
- `REBASE_AFTER`

Do not rely only on textual merge conflicts. A behavior PR using an architecture another PR replaces is a dependency even when Git reports a clean merge.

## Health and migration

Classify current-main drift, stale ownership, missing validation, unresolved review, architecture migration debt, partial supersession, and foundation dependencies. Prefer repairing or correctly blocking existing work before producing replacements.

Preserve useful implementation, tests, documentation, evidence, and authorship when consolidating work. Keep independent acceptance units separate when stacking is clearer than absorption.

## Mutation rules

- Respect active Codex/ChatGPT/human leases and ownership markers.
- Never mutate another active worker's branch just because reconciliation discovered it.
- When authorized to repair a branch, checkpoint and push meaningful reconciliation progress.
- Re-run local CI after rebases, migrations, or conflict repair before claiming the branch is healthy.
- Record exact head/base relationships and the next owner/action so another worker can resume safely.

## New-work gate

A reconciliation run may declare a repository/portfolio stable only when materially relevant open work has known ownership, dependencies, migration state, and next actions, with no unexplained stale/overlapping/superseded state remaining.

Do not clear a reconciliation freeze because one pass found nothing easy to change.
