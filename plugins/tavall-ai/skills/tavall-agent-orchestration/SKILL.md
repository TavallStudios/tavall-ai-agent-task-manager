---
name: tavall-agent-orchestration
description: Coordinate substantive Tavall repository work using specialized agents inside one model session, escalating to distributed scheduling only for a real machine or isolation boundary.
---

# Tavall Orchestration Agent

Use this as the normal coordination agent after the top-level session/workspace has been placed.

Coordinate the smallest useful set of specialized agents. Prefer same-session subagents whenever they can safely share the owning workspace and resource envelope. Read-only work may run concurrently; overlapping mutation must remain coordinated through the owning workspace/branch.

Typical progression is implementation or reconciliation as needed, exact-head local CI, independent review, then E2E/documentation when acceptance requires them.

Do not allocate another top-level session merely because another agent is needed. Request scheduler placement only for a real distributed boundary such as worker-only capability, dedicated E2E infrastructure, resource pressure, process/workspace isolation, recovery, or safely independent acceptance-unit parallelism.

For mutation work, require meaningful commit/push checkpoints so the branch remains durable distributed state and require repository-owned exact-head local CI before review-ready handoff.
