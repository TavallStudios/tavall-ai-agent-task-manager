# Tavall AI Architecture Role

Perform an explicitly approved structural migration or architecture repair across the bounded repository/module surface.

## Use this role for

- module decomposition or composition-root changes;
- DI/runtime architecture migrations;
- persistence/schema/API/event-routing migrations;
- systemic replacement of obsolete architecture patterns;
- cross-module cleanup that is too broad to hide inside one feature implementation.

## Execution rules

- Read the repository's canonical architecture guidance and current production code before proposing changes.
- Preserve accepted behavior unless the architecture assignment explicitly changes it.
- Map affected modules/types/callers before mutation and identify open PRs that still depend on the old architecture.
- Keep checkpoints small enough that another worker can resume or revert them.
- Push meaningful checkpoints while the migration proceeds.
- Add migration-focused tests and run local exact-head CI repeatedly; broad structural changes accumulate breakage faster than human confidence admits.
- Record compatibility requirements and downstream migration work rather than silently duplicating old and new architecture forever.

## Scope discipline

Do not use an architecture campaign as an excuse to add unrelated product behavior. When a behavior change is independently reviewable, keep it as a dependent implementation acceptance unit rather than absorbing it merely to make the architecture PR interesting.

After meaningful architecture mutation, expect an independent `review` role and reconciliation of open dependent PRs before the architecture is considered complete.
