---
name: tavall-skill-orchestrator
description: Use as the entry-point router for Tavall-related work. Resolve the smallest required Tavall skill graph from the live installed/runtime surface, route Tavall prompts through the memory foundation when available, require tavall-git-workflow for repository/PR policy, route substantive repository work through the Tavall AI agent family when exposed, require exact-head Tavall local CI after diff-producing engineering work, and surface degraded or missing dependencies instead of silently skipping them.
---

# Tavall Skill Orchestrator

Use this skill first for Tavall-related requests. It is a router and health gate, not a replacement for specialist skills.

## Core invariant

**Orchestrate, do not absorb.**

Do not copy the full procedures of domain skills into this file. Resolve the skill graph, load the relevant specialists, verify required capabilities, and let each specialist own its domain.

## Entry behavior

For every Tavall-related prompt:

1. Classify the request as conversational, investigative, planning, implementation, review, validation, deployment, design, repository, or mixed work.
2. Discover the currently exposed installed skills, connected plugins, repository-local skills, and callable capability surfaces before trusting a remembered registry entry.
3. Route through `tavall-memory-plane` when available. The memory skill decides whether the prompt needs BOOTSTRAP, INVESTIGATION, WRITEBACK, REVIEW, VALIDATION, or no provider call.
4. If the memory skill/runtime is unavailable, mark memory `DEGRADED` and continue from current repository/runtime evidence where safe. Never pretend memory hydration occurred.
5. If branch, commit, pull-request, staging, reconciliation, promotion, or repository mutation may occur, require `tavall-git-workflow` before making Git/GitHub policy decisions.
6. For substantive Tavall repository work, route through `tavall-ai` and then `tavall-agent-orchestration` when that installed agent family is exposed. Let orchestration select the smallest useful acceptance-unit specialists.
7. Do not use `tavall-agent-scheduler` merely because another specialist is useful. Scheduler placement is for a real worker, resource, isolation, recovery, or safely independent parallel-work boundary. Do not confuse it with `tavall-ai-distributed-execution`, which routes one already-authorized model call.
8. Add domain specialists such as Web Agent, Minecraft Builder, Java Tools, or Tavall Cloud only when the task crosses their ownership boundary.
9. Resolve required dependencies topologically and execute through the selected specialists/tools.
10. If Tavall engineering work produced a diff, require `tavall-local-ci` or the current typed Tavall Cloud exact-head LOCAL_CI equivalent before claiming completion. A prior successful job for another SHA is stale evidence.
11. Validate both the domain result and its integration boundary.
12. After verified reusable work, route through `tavall-memory-plane` WRITEBACK when available. Do not persist ordinary tool chatter or unverified conclusions.

## Availability states

Every required or selected skill/capability has one state:

- `AVAILABLE`: identity resolved and required runtime capabilities are exposed.
- `DEGRADED`: skill/capability is known, but part of its preferred runtime is unavailable and a safe fallback exists.
- `MISSING`: required skill/capability cannot be resolved after discovery.
- `SKIPPED_BY_SCOPE`: known skill is irrelevant to the current task.
- `BLOCKED`: policy or a missing non-substitutable dependency prevents safe execution.

Required dependencies may never disappear silently. Optional helpers may be skipped when their absence does not change correctness, but material quality loss must be reported.

## Discovery order

Resolve skills/capabilities from current evidence in this order:

1. Explicitly installed skills/plugins exposed to the current agent.
2. Repository-local `SKILL.md`, plugin manifests, `AGENTS.md` / `AGENT.MD`, and tool catalogs.
3. Connected Tavall Function Catalog and Tavall Cloud surfaces.
4. Registry aliases in `registry.yaml`.
5. Historical memory only as a lead, never as proof of current installation.

The live installed skill identity wins over a stale source pointer. When an alias resolves to a current exact skill identity, use that exact identity for the remainder of the run.

## Foundation routing

### Memory

`tavall-memory-plane` is the Tavall context foundation. Route Tavall prompts through it when available while allowing that skill to decide whether provider work is warranted. For substantive engineering, architecture, debugging, deployment, review, planning, or continuation work, BOOTSTRAP should occur once early when `memoryContext` or its current equivalent is exposed.

Do not count AgentTaskManager prompt-thread/task-runtime context as memory-plane hydration unless the memory-plane skill explicitly declares equivalence.

### Git

`tavall-git-workflow` owns Tavall branch, PR, stacking, staging, reconciliation, promotion, and GitHub workflow decisions. Domain skills delegate those policy decisions instead of maintaining divergent Git doctrine.

### Exact-head completion

`tavall-local-ci` owns the ChatGPT-facing completion policy for diff-producing Tavall engineering work when installed. If the skill itself is unavailable but Tavall Cloud exposes the same typed LOCAL_CI contract, mark the skill `DEGRADED` and use that typed equivalent. If neither exists, do not claim exact-head Tavall CI acceptance.

## Tavall AI skill family

When the installed Tavall AI plugin exposes the current skill family:

- `tavall-ai`: top-level Tavall AI operating/runtime router.
- `agent-task-manager`: narrow AgentTaskManager harness/task-runtime specialist.
- `tavall-agent-orchestration`: normal coordination specialist for substantive repository work.
- `tavall-agent-implementation`: bounded implementation acceptance unit.
- `tavall-agent-review`: independent exact-head review.
- `tavall-agent-reconciliation`: PR/staging/topology and drift reconciliation.
- `tavall-agent-e2e`: realistic runtime/E2E acceptance.
- `tavall-agent-architecture`: explicitly approved structural migration/repair.
- `tavall-agent-documentation`: technical/evidence documentation.
- `tavall-agent-scheduler`: durable distributed worker/session placement only.
- `tavall-agent-builder`: Tavall AI coordination around authoritative Builder implementation.
- `tavall-ai-distributed-execution`: provider/runtime routing for one authorized model call.

If multiple installed skill files claim the same exact identity, treat that as a health conflict. Prefer the explicit top-level operating entrypoint declared by the installed plugin/repository and reconcile the duplicate rather than silently letting load order decide policy.

## Domain ownership

Use `registry.yaml` for the bootstrap routing map. Important boundaries:

- `tavall-ai` and its `tavall-agent-*` family own Tavall AI agent/runtime coordination.
- `agent-task-manager` owns the narrower harness/task-runtime workflow and its prompt-thread context.
- `tavall-memory-plane` owns Tavall durable context hydration, memory investigation, reviewed writeback, and memory-plane validation.
- `tavall-git-workflow` owns Git/PR/staging/reconciliation/promotion policy.
- `tavall-local-ci` owns exact-final-HEAD completion policy when installed.
- `minecraft-builder` owns Minecraft/Builder Studio build implementation and build-oriented verification; `tavall-agent-builder` coordinates around it without duplicating it.
- `rendering-builder-replays` owns replay/render-specific Builder verification.
- `tavall-web-agent` owns web/frontend design and browser acceptance workflow.
- `impeccable` is a visual-quality helper bundle where available.
- `tavall-java-tools` is the Java/JVM/Gradle/testing capability family.
- `tavall-cloud` owns infrastructure, environments, deployment, machines, services, sandboxes, durable jobs, and typed local-CI execution.

## Conflict rules

If two skills claim the same operation:

1. Prefer an explicit repository-local authority declaration that matches the live installed plugin.
2. Otherwise prefer the skill whose registry ownership is narrower and more specific.
3. Foundations own cross-cutting policy; domain skills own domain implementation.
4. Helpers cannot override canonical policy.
5. Surface unresolved identity/authority conflicts instead of choosing by load order or vibes.

## Health gate

Before consequential work, verify selected required skills are resolvable and their non-optional runtime dependencies exist. See `references/health-and-routing.md`. Re-run the gate if the task crosses a new domain mid-run.

A completed task that silently omitted a required foundation or exact-head completion gate is an orchestration failure even if the resulting code happens to compile.
