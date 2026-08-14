---
name: tavall-ai
description: Use for substantive Tavall AI runtime, agent, coding, PR, architecture, review, reconciliation, E2E, documentation, distributed execution, or Builder work while preserving Tavall Cloud authority.
---

# Tavall AI

Use this as the normal Tavall AI operating entry point.

## Architecture

Keep these boundaries explicit:

- **bootstrap** discovers and validates installed Tavall agents and Tavall AI runtime capability modules;
- **runtime** is a launchable AI/model execution process identity such as `NODE_AGENT` or `CHATGPT_WEB`;
- **agent** is a reusable behavior/instruction/function-requirement package such as orchestration, implementation, review, E2E, scheduler, or Builder;
- **runtime capability module** owns actual AI runtime behavior such as distributed model/provider routing;
- **execution provider** adapts an authorized model/process backend such as Codex;
- **scheduler agent** describes durable workload/session placement and recovery but does not contain a model runtime.

Agents are not AIs. The parent Tavall AI runtime performs model execution with the agents, functions, executables, workspace, and authority granted to that execution.

## Routing

- For substantive repository work, start with `tavall-agent-orchestration`.
- Use the specialized `tavall-agent-*` skills for their acceptance-unit responsibilities.
- Use `tavall-agent-scheduler` only for distributed worker/top-level-session placement or recovery.
- Use `tavall-ai-distributed-execution` for a bounded AI/model call that may route across authorized node or web runtimes.
- Use `tavall-agent-builder` for Builder domain work and preserve the Project Novus Builder implementation/artifact boundary.
- Prefer multiple specialized agents/subagents inside one model session when they can safely share the owning workspace and resources.
- Do not allocate another machine merely because another agent is useful.

## Capability model

Functions, executables, and runtime capabilities are different things.

Tavall Java/application code exposes genuinely callable typed operations through Function Catalog registration/annotations. Function Catalog owns canonical schemas, invocation, narrowed callable views, policy/audit hooks, and MCP projection.

Executable capabilities such as Git, GitHub CLI, Java, Gradle, Builder Studio, browser/runtime helpers, or other CLIs remain tools granted/materialized by the owning Cloud/runtime authority. Do not manufacture MCP wrappers merely to turn a CLI command into a function.

Tavall Cloud determines DEVELOPMENT eligibility, durable job authority, workspace/process/sandbox/network authority, executable/credential grants, resource capacity, local CI execution, and external-operation authorization.

## Repository work

Use current production code and architecture as the source of truth. Mutation agents must commit and push meaningful checkpoints so another authorized worker can resume from Git after session or machine loss.

Use repository-owned local CI against the exact head before review-ready handoff. GitHub may display resulting status/evidence, but hosted workflow YAML is not the source of Tavall build logic.
