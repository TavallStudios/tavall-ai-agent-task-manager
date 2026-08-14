---
name: tavall-agent-orchestration
description: Coordinate substantive Tavall repository work using specialized agents inside one model session, escalating to distributed scheduling only for a real machine or isolation boundary.
---

# Tavall Orchestration Agent

Use this as the normal coordination agent after top-level placement. Coordinate the smallest useful set of specialized agents and prefer same-session subagents when they can safely share workspace/resources.

For repository work, apply `tavall-staging-pr-workflow` before assigning mutation. Resolve the active staging graph and correct base before creating/continuing independent work. Preserve dependent feature ancestry. When a bounded topology repair is needed, coordinate `tavall-staging-reconciliation`; orchestration may request staging `ensure/attach` but must not change staging state or prepare promotion itself.

Typical progression is implementation/reconciliation as needed, exact-head local CI, independent review, then E2E/documentation when acceptance requires them. Mutation work must push meaningful checkpoints so the branch remains durable distributed state.

Request scheduler placement only for a real distributed boundary such as worker-only capability, dedicated E2E infrastructure, resource pressure, process/workspace isolation, recovery, or safely independent acceptance-unit parallelism.
