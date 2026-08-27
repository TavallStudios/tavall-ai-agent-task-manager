# Tavall Skill Orchestration Bundle

This bundle provides Tavall-wide skill routing plus reusable Git/PR policy routing.

The router exists because having a skill in a repository, plugin, or memory does not guarantee that an agent will invoke the correct current identity. Required foundations, the live Tavall AI agent family, domain specialists, and completion gates therefore form an explicit dependency graph with observable health states.

## Components

- `tavall-skill-orchestrator`: Tavall entry-point discovery, dependency ordering, capability health, Tavall AI agent-family routing, and missing-skill behavior.
- `tavall-git-workflow`: shared Git/PR/stacking/staging/reconciliation policy backed by the current canonical `GIT_WORKFLOW.md`.
- `registry.yaml`: bootstrap inventory for current exact skills, aliases, helper bundles, conditional completion gates, and capability families. Runtime discovery is authoritative over stale aliases.

## Current Tavall AI shape

The live Tavall AI plugin is the top-level AI/runtime domain entry point. Substantive repository work normally routes from `tavall-ai` into `tavall-agent-orchestration`, which selects bounded implementation/review/reconciliation/E2E/architecture/documentation specialists. `tavall-agent-scheduler` is reserved for genuine distributed placement; `tavall-ai-distributed-execution` routes individual authorized model calls. `agent-task-manager` is a narrower harness/task-runtime specialist.

For diff-producing Tavall engineering work, `tavall-local-ci` or the same typed Tavall Cloud exact-head LOCAL_CI contract is the completion boundary.

The bundle deliberately does not clone specialist skill bodies. Specialists remain independently versioned and own their domains.
