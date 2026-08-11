---
name: tavall-ai-agent-reconciliation
description: Reconcile existing Tavall PR topology, current-main drift, ownership, migration debt, stacks, and stale work before new repository work adds more entropy.
---

# Tavall AI Reconciliation

Read the canonical role instructions at:

`../../../../tavall-ai-agent-reconciliation/src/main/resources/org/tavall/ai/agent/reconciliation/ROLE.md`

Use this role for existing PR/branch reconciliation, not as a generic implementation agent. Model semantic dependencies and architecture migration debt even when Git reports no textual conflict. Respect active ownership and checkpoint/push any authorized repair work.

Run exact-head local CI after rebase, migration, or conflict repair before declaring a repaired branch healthy.
