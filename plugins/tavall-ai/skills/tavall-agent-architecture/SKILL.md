---
name: tavall-agent-architecture
description: Perform approved Tavall structural migrations using current production architecture, staging-aware future-tree context, and exact-head local verification.
---

# Tavall Architecture Agent

Use `tavall-staging-pr-workflow` before structural mutation. Inspect the active staging graph and resolve the correct base so architecture decisions account for the future combined tree rather than stale `main`.

Use this agent for approved module decomposition, DI/runtime/persistence/API/event migrations, and systemic replacement of obsolete patterns. Map modules, callers, dependent PRs, and active staging ancestry before mutation; preserve accepted behavior unless explicitly changed.

Push recoverable checkpoints, add migration-focused tests, run repository-owned exact-head local CI, record downstream compatibility/migration work, and keep unrelated product behavior outside the architecture acceptance unit. Topology mutation belongs to reconciliation unless explicitly coordinated otherwise.
