---
name: tavall-staging-promotion
description: Prepare a Tavall staging root for separately authorized promotion by freezing scope and collecting exact-head topology, validation, review, risk, and rollback evidence.
---

# Tavall Staging Promotion

Use this shared skill only at the staging-root promotion boundary.

## Canonical functions

- `repository_staging_set_state`
- `repository_staging_validate`
- `repository_staging_prepare_promotion`

Promotion evidence must also include the selected lane/environment identity:

- lane ID and environment ID/generation;
- complete source snapshot digest and participating repository SHAs;
- desired/observed environment state and component topology;
- required validation profiles whose PASS records match that exact digest.

`repository_staging_prepare_promotion` **does not merge** a staging PR, does not deploy anything, and does not grant promotion authority.

## Promotion preparation

1. Freeze the intended staging scope with an authorized `repository_staging_set_state` transition to `FROZEN` when the integration set is ready for acceptance.
2. Validate the staging graph and repair topology errors before acceptance.
3. Run the repository-owned local verification against the **exact staging head**.
4. Resolve/inspect the matching environment generation and verify every required validation PASS is pinned to its exact source digest.
5. Collect required independent review, migration/compatibility findings, E2E/runtime evidence, risk notes, and rollback/reconciliation state.
6. Call `repository_staging_prepare_promotion` to collect exact-head checks, open child PRs, topology findings, and blockers.
7. Move to `PROMOTING` only when policy/ownership permits and the preparation result is unblocked.
8. Actual merge/promotion to `main` remains a separate explicitly authorized source-control action.
9. Deployment remains a separate Tavall Cloud/release action after source promotion where applicable.

After a staging root promotes, establish/resolve the next active staging root rather than continuing to attach new work to a completed historical root.

Environment destruction never closes a persistent staging PR, and closing a staging PR never destroys retained environment or validation history.
