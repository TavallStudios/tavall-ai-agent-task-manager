---
name: tavall-ai
description: Use for substantive Tavall AI coding, PR, architecture, review, reconciliation, E2E, documentation, distributed execution, or domain-agent work while preserving Tavall Cloud authority.
---

# Tavall AI

Use this as the normal Tavall AI operating entry point.

## Architecture

Keep these boundaries explicit:

- **bootstrap** composes installed Tavall AI modules;
- **runtime** is a launchable AI execution process identity such as `NODE_AGENT` or `CHATGPT_WEB`;
- **role module** supplies reusable behavior such as orchestration, implementation, review or E2E;
- **domain module** supplies durable domain behavior such as Builder;
- **execution provider** adapts an authorized model/process backend such as Codex;
- **distributed execution** routes an AI call among already-authorized node/web execution surfaces;
- **scheduler** places durable workloads/sessions and does not own model/provider routing.

Roles/modules are not themselves independent AIs. The actual Codex/model runtime performs the reasoning/execution with the modules, functions, executables and workspace authority granted to that execution.

## Routing

- For substantive repository work, start with `tavall-ai-agent-orchestration`.
- Use specialized role skills for the actual acceptance-unit responsibilities.
- Use `tavall-ai-agent-scheduler` only when work needs distributed worker/top-level-session placement or recovery.
- Use `tavall-ai-distributed-execution` when an existing job/module needs an AI/model call that may route across authorized node or web runtimes.
- Use `tavall-ai-builder` for Builder domain work and preserve the existing Project Novus Builder implementation/artifact boundary.
- Prefer multiple specialized role modules/subagents inside one model session when they can safely share the owning workspace and resources.
- Do not allocate another machine merely because another role is useful.

## Capability model

Functions, CLIs and runtime capabilities are different things.

Tavall Java/application code should expose genuinely callable typed operations through Function Catalog registration/annotations. Function Catalog owns canonical schemas, invocation, narrowed callable views, policy/audit hooks and MCP projection.

Executable capabilities such as Git, GitHub CLI, Java, Gradle, browser/runtime helpers or other CLIs remain execution tools granted/materialized by the owning Cloud/runtime authority; do not manufacture MCP wrappers merely to turn a CLI command into a function.

Tavall Cloud determines DEVELOPMENT-node eligibility, durable job authority, workspace/process/sandbox/network authority, executable/credential grants, resource capacity, local CI execution and external operation authorization.

## Repository work

Use current production code and architecture as the source of truth. Mutation roles must commit and push meaningful checkpoints while working so another scheduled worker can resume from Git after session or machine loss.

Use repository-owned local CI against the exact head before review-ready handoff. GitHub may display the resulting Check/status, but GitHub-hosted workflows are not the default Tavall CI executor.
