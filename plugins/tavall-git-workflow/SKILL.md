---
name: tavall-git-workflow
description: Use for Tavall branch, commit, pull-request, stacking, staging, reconciliation, review, promotion, hotfix, and GitHub workflow decisions. Load the current canonical GIT_WORKFLOW.md plus stricter repository-local rules, inspect the live PR/branch graph before acting, preserve durable PR work surfaces, use first-class stacking and truthful staging evidence, and keep main promotion separate from deployment.
---

# Tavall Git Workflow

This skill operationalizes Tavall's shared Git/PR workflow. It does not replace the canonical policy document.

## Authority

Before consequential Git/PR decisions, read the current canonical policy:

- repository: `TavallStudios/tavall-project-novus`
- path: `docs/quality/GIT_WORKFLOW.md`

Then read stricter repository-local authority such as `AGENTS.md`, `AGENT.MD`, `CONTRIBUTING.md`, synchronization contracts, release procedures, staging manifests, and deployment runbooks.

If the canonical document cannot be read, mark policy `DEGRADED`. Do not silently substitute an old cached copy for current policy.

## Preflight graph inspection

Before creating a branch or PR, retargeting a PR, composing staging, reconciling branches, or promoting work, inspect at least the relevant:

- current branch and remote head;
- existing PR for the branch;
- open same-scope or overlapping PRs;
- parent/child PR relationships;
- integration/staging membership;
- merge/base state and conflicts;
- configured validation/check state;
- repository-specific release/synchronization constraints.

## Classify before acting

Classify the intended work relationship:

- `same_scope`: continue the existing PR/branch.
- `independent`: keep/create a parallel PR against the correct integration target.
- `dependent`: stack the child PR on the required unmerged parent branch.
- `overlapping`: explicitly reconcile shared scope/ancestry rather than duplicating implementation.
- `superseded`: close or redirect obsolete work with traceable explanation.

Do not create `v2`, `replacement`, or similar duplicate PRs merely because an existing PR needs repair, rebase, retargeting, architectural migration, or another agent handoff.

## Pull requests are durable work surfaces

Treat the remote branch and its open PR as one durable unit of work. Continue coherent implementation, validation, review, dependency metadata, and reconciliation on that PR until merged, superseded, or intentionally abandoned.

There is no organization-wide low PR-count ceiling and no global reconciliation freeze. Locks/reconciliation scope are local to the affected graph.

## Stacked PRs

Stacking is first-class when unmerged work has a real dependency.

Mechanically, a child targets the parent PR branch. Record the parent/child relationship and expected merge order in PR/staging evidence.

After a parent merges:

1. update the child branch from the parent's new destination;
2. retarget the same child PR to the appropriate integration target;
3. verify the resulting diff contains only the child's intended changes;
4. rerun affected validation;
5. update staging/dependency evidence;
6. continue using the same child PR.

Do not flatten a dependency graph by copying parent commits into unrelated branches when Git ancestry can express the relationship.

## Staging

Staging is integration state, not a mandatory single queue.

Use staging manifests/files and/or actual staging branches when the repository's workflow requires integrated composition, validation, release candidates, or synchronization.

Hierarchical staging such as feature -> sub-staging/domain staging -> runtime/product staging -> unified staging is valid when the repository's current graph defines those layers. Do not manufacture unnecessary staging layers merely because the names exist.

Before adding a feature PR to staging, check whether it is already represented. Before adding a staging PR to a unified staging PR, check whether it is already represented. Avoid duplicate composition.

## Validation and evidence

Record validation truthfully against exact relevant heads/compositions. Distinguish:

- validation of one PR in isolation;
- validation of a stacked child with its parent;
- validation of an integrated staging composition;
- failures intrinsic to one PR versus composition-only failures.

Never report compile/test/runtime acceptance that was not actually observed.

## Review and promotion

Follow the current canonical review path. In the current shared model, accountable review is either independent qualified human approval or an authorized owner self-review record for owner-authored work when permitted.

Automated/AI review supports but does not replace accountable review unless current canonical policy explicitly changes.

Promotion to `main` and production deployment are separate decisions. A commit reaching `main` does not imply a service was deployed.

## Owner authority and hotfixes

Use owner bypass, direct-main changes, history rewrites, or check overrides only when current policy and repository-specific rules permit them and the authorized owner accepts responsibility. Preserve traceability and reconcile affected open PR/staging state afterward.

Urgent hotfixes should start from current `main`, remain minimal, validate what is available, and reconcile affected active branches/staging state.

## Delegation

Use the available Git, GitHub, and Tavall Cloud primitives for mechanics. This skill owns the workflow decision, not the implementation of another Git client.
