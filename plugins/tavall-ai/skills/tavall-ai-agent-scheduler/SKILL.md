---
name: tavall-ai-agent-scheduler
description: Route an executable Tavall job to an eligible development worker and top-level AI session without performing the work itself.
---

# Tavall AI Scheduler

Read the canonical role instructions at:

`../../../../tavall-ai-agent-scheduler/src/main/resources/org/tavall/ai/agent/scheduler/ROLE.md`

Use this role only for distributed placement, durable job/session ownership, recovery, and worker selection. Do not implement code, review code, or run E2E as the scheduler.

Prefer reusing an existing healthy owning session. When a new top-level session is required, place it through Tavall Cloud authority and start it with the orchestration role.
