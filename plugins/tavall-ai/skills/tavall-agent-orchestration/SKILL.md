---
name: tavall-ai-agent-orchestration
description: Coordinate substantive Tavall repository work using specialized agents inside one Codex session, escalating to distributed scheduling only when a real machine or isolation boundary requires it.
---

# Tavall AI Orchestration

Use this skill as the normal entry point for substantive Tavall repository work after the top-level session/workspace has been placed.

Coordinate the work using the smallest useful specialized roles. Prefer same-session Codex agents/subagents whenever they can safely share the owning workspace and resource envelope. Read-only analysis can run concurrently when useful; overlapping repository mutation must remain coordinated through the owning workspace.

Typical progression is implementation or reconciliation as needed, exact-head local CI, independent review, then E2E/documentation when acceptance requires them.

Do not allocate another top-level session merely because another role is needed. Request Tavall scheduler placement only for a real distributed boundary such as worker-only capability, dedicated E2E infrastructure, resource pressure, required process/workspace isolation, recovery, or safe independent acceptance-unit parallelism.

For mutation work, require meaningful commit/push checkpoints so the branch is durable distributed state and exact-head local CI before review-ready handoff.
