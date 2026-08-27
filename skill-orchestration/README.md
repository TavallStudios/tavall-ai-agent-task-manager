# Tavall Skill Orchestration Bundle

This bundle introduces a small Tavall-wide skill router plus a reusable Git/PR workflow skill.

The router exists because having a skill in a repository or memory does not guarantee that an agent will invoke it. Required foundations and domain specialists therefore become an explicit dependency graph with observable health states.

## Components

- `tavall-skill-orchestrator`: Tavall entry-point routing, discovery, dependency ordering, capability health, and missing-skill behavior.
- `tavall-git-workflow`: shared Git/PR/staging/reconciliation operational skill backed by the current canonical `GIT_WORKFLOW.md`.
- `registry.yaml`: bootstrap inventory covering current exact skills, known aliases, helper bundles, and capability families. Runtime discovery is authoritative over stale aliases.

The bundle deliberately does not clone specialist skill bodies. Specialists remain independently versioned and own their domains.
