---
name: tavall-ai
description: Use for substantive Tavall coding, PR, architecture, review, reconciliation, E2E, documentation, or distributed-agent work. Route work through Tavall AI roles and Function Catalog capabilities while preserving Tavall Cloud authority.
---

# Tavall AI

Use this as the normal Tavall AI operating entry point.

## Routing

- For substantive repository work, start with `tavall-ai-agent-orchestration`.
- Use specialized role skills for the actual acceptance-unit responsibilities.
- Use `tavall-ai-agent-scheduler` only when work needs distributed worker/top-level-session placement or recovery.
- Prefer multiple specialized Tavall agents/subagents inside one Codex session when they can safely share the owning workspace and resources.
- Do not allocate another machine merely because another role is useful.

## Capability model

Tavall Java/application code should expose typed AI/consumer operations through Function Catalog registration and annotations. Role skills and role modules request those functions; they do not implement a parallel tool framework.

Function Catalog determines the callable function view. Tavall Cloud determines DEVELOPMENT-node placement, workspace/process/sandbox authority, resource capacity, local CI execution, and external operation authorization.

## Repository work

Use current production code and architecture as the source of truth. Mutation roles must commit and push meaningful checkpoints while working so another scheduled worker can resume from Git after session or machine loss.

Use repository-owned local CI against the exact head before review-ready handoff. GitHub may display the resulting Check/status, but GitHub-hosted workflows are not the default Tavall CI executor.
