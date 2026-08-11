---
name: tavall-ai-agent-architecture
description: Perform an explicitly approved Tavall cross-module architecture migration or structural repair using current production architecture and exact-head local verification.
---

# Tavall AI Architecture

Use this role for explicitly approved structural work that should not be smuggled into a feature PR: module decomposition, DI/runtime/persistence/API/event migrations, and systemic replacement of obsolete patterns.

Read the repository's canonical architecture guidance and real production code first. Map affected modules, callers, and dependent PRs before mutation; preserve accepted behavior unless the assignment explicitly changes it.

Push recoverable checkpoints, add migration-focused tests, run local exact-head CI repeatedly, record downstream compatibility/migration work, and keep unrelated product behavior outside the architecture acceptance unit. Expect independent review and dependent-PR reconciliation after meaningful architecture mutation.
