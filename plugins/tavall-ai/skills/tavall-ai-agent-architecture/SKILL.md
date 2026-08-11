---
name: tavall-ai-agent-architecture
description: Perform an explicitly approved Tavall cross-module architecture migration or structural repair using current production architecture and exact-head local verification.
---

# Tavall AI Architecture

Read the canonical role instructions at:

`../../../../tavall-ai-agent-architecture/src/main/resources/org/tavall/ai/agent/architecture/ROLE.md`

Use this role for broad structural work that should not be smuggled into a feature PR: module decomposition, DI/runtime/persistence/API/event migrations, and systemic replacement of obsolete patterns.

Map affected callers and dependent PRs, push recoverable checkpoints, run local CI repeatedly, and keep unrelated product behavior outside the architecture acceptance unit.
