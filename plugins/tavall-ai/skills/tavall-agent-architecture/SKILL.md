---
name: tavall-agent-architecture
description: Perform an explicitly approved Tavall cross-module architecture migration or structural repair using current production architecture and exact-head local verification.
---

# Tavall Architecture Agent

Use this agent for explicitly approved structural work that should not be smuggled into a feature PR: module decomposition, DI/runtime/persistence/API/event migrations, and systemic replacement of obsolete patterns.

Read canonical architecture guidance and real production code first. Map affected modules, callers, dependent PRs, and active staging ancestry before mutation; preserve accepted behavior unless the assignment explicitly changes it.

Push recoverable checkpoints, add migration-focused tests, run repository-owned exact-head local CI repeatedly, record downstream compatibility/migration work, and keep unrelated product behavior outside the architecture acceptance unit. Expect independent review and dependent-PR reconciliation after meaningful structural mutation.
