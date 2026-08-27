---
name: tavall-skill-orchestrator
description: Use as the entry-point router for Tavall-related work. Resolve the smallest required Tavall skill graph, route every Tavall prompt through the Tavall memory skill when it is available, require tavall-git-workflow whenever repository or PR state may change, discover domain specialists and helper bundles, verify dependencies are actually exposed, and report degraded or missing capabilities instead of silently skipping required skills.
---

# Tavall Skill Orchestrator

Use this skill first for Tavall-related requests. It is a router and health gate, not a replacement for specialist skills.

## Core invariant

**Orchestrate, do not absorb.**

Do not copy the full procedures of domain skills into this file. Resolve the skill graph, load the relevant specialists, verify their required capabilities, and let each specialist own its domain.

## Entry behavior

For every Tavall-related prompt:

1. Identify whether the request is conversational, investigative, planning, implementation, review, validation, deployment, design, or repository work.
2. Discover the currently available Tavall skill/capability surface before assuming a remembered skill is installed.
3. Route through `tavall-memory-plane` when that skill is available. The memory skill decides whether the prompt needs BOOTSTRAP, INVESTIGATION, WRITEBACK, REVIEW, VALIDATION, or no provider call.
4. If `tavall-memory-plane` or its required runtime capability is unavailable, mark memory `DEGRADED` and continue from current repository/runtime evidence. Never pretend memory hydration occurred.
5. If branch, commit, pull-request, staging, reconciliation, promotion, or repository mutation may occur, require `tavall-git-workflow` before making Git/GitHub decisions.
6. Route to the smallest set of domain specialists required for the task.
7. Resolve dependencies in topological order. Do not fan out to unrelated skills merely because they exist.
8. Execute through the specialist skills and available tools.
9. Validate both the domain result and its integration boundary.
10. After verified reusable work, route through `tavall-memory-plane` WRITEBACK when available. Do not persist ordinary tool chatter or unverified conclusions.

## Availability states

Every required or selected skill/capability has one state:

- `AVAILABLE`: identity resolved and required runtime capabilities are exposed.
- `DEGRADED`: skill or capability is known, but part of its preferred runtime is unavailable; safe fallback exists.
- `MISSING`: required skill/capability cannot be resolved after discovery.
- `SKIPPED_BY_SCOPE`: known skill is irrelevant to the current task.
- `BLOCKED`: policy or a missing non-substitutable dependency prevents safe execution.

Required dependencies may never disappear silently. Optional helpers may be skipped when their absence does not change correctness, but material quality loss must be reported.

## Discovery order

Resolve skills/capabilities from current evidence in this order:

1. Explicitly installed skills/plugins exposed to the current agent.
2. Repository-local `SKILL.md`, plugin manifests, `AGENTS.md` / `AGENT.MD`, and tool catalogs.
3. Connected Tavall capability catalogs and Tavall Cloud surfaces.
4. Registry aliases in `registry.yaml`.
5. Historical memory only as a lead, never as proof of current installation.

When an alias resolves to a current exact skill identity, use the exact identity for the remainder of the run.

## Foundation routing

### Memory

`tavall-memory-plane` is the Tavall context foundation. Route every Tavall prompt through it when available, while allowing that skill to decide whether a provider call is warranted.

For substantive engineering, architecture, debugging, deployment, review, planning, or continuation work, memory BOOTSTRAP should occur once early when `memoryContext` or its current equivalent is exposed.

Do not count `tavall-ai` prompt-thread/task-runtime context as Tavall memory-plane hydration unless the memory-plane skill explicitly says it satisfies the same contract.

### Git

`tavall-git-workflow` owns Tavall branch, PR, stacking, staging, reconciliation, promotion, and GitHub workflow decisions. Domain skills must delegate those decisions rather than maintaining divergent Git doctrine.

## Domain ownership

Use `registry.yaml` for the initial routing map. Important ownership boundaries:

- `tavall-ai`: AgentTaskManager/harness lifecycle, task runtime, function-catalog/tool execution, and its own prompt-thread runtime context.
- `tavall-memory-plane`: Tavall durable context hydration, memory investigation, reviewed writeback, and memory-plane validation.
- `tavall-git-workflow`: Git/PR/staging/reconciliation/promotion policy and execution decisions.
- `minecraft-builder`: Minecraft/Builder Studio build changes and build-oriented verification.
- `rendering-builder-replays`: replay/render-specific Builder Studio verification.
- `tavall-web-agent`: web/frontend design and browser acceptance workflow, resolved by discovery when the exact installed identity differs.
- `impeccable`: web/build visual-quality helper bundle where available.
- `tavall-java-tools`: Java/JVM/Gradle/testing capability family, resolved by discovery.
- `tavall-cloud`: Tavall infrastructure, environment, deployment, machine, and typed cloud capability family, resolved by discovery.

## Conflict rules

If two skills claim the same operation:

1. Prefer an explicit repository-local authority declaration.
2. Otherwise prefer the skill whose registry ownership is narrower and more specific.
3. Foundations own cross-cutting policy; domain skills own domain implementation.
4. Never let a helper bundle override canonical policy.
5. Surface unresolved authority conflicts instead of choosing by vibes.

## Health gate

Before consequential work, verify selected required skills are resolvable and their non-optional runtime dependencies exist. See `references/health-and-routing.md`.

The orchestrator must make missing-skill behavior observable. A completed task that silently omitted a required foundation is an orchestration failure even if the resulting code happens to compile.
