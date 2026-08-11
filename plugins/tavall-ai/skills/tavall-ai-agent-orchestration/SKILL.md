---
name: tavall-ai-agent-orchestration
description: Coordinate substantive Tavall repository work using specialized agents inside one Codex session, escalating to distributed scheduling only when a real machine or isolation boundary requires it.
---

# Tavall AI Orchestration

Use this skill as the normal entry point for substantive Tavall repository work.

Read the canonical role instructions at:

`../../../../tavall-ai-agent-orchestration/src/main/resources/org/tavall/ai/agent/orchestration/ROLE.md`

Then coordinate the work using the smallest useful specialized roles. Prefer same-session Codex agents/subagents whenever they can safely share the owning workspace and resource envelope.

Do not allocate another top-level session merely because another role is needed. Invoke the Tavall scheduler path only for a real distributed boundary such as worker-only capability, dedicated E2E infrastructure, resource pressure, required process/workspace isolation, or safe independent acceptance-unit parallelism.

For mutation work, require meaningful commit/push checkpoints and exact-head local CI before review-ready handoff.
