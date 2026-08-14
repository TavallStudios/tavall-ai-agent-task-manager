---
name: tavall-ai
description: Use for substantive Tavall AI runtime, agent, coding, PR, architecture, review, reconciliation, E2E, documentation, distributed execution, staging, or Builder work while preserving Tavall Cloud authority.
---

# Tavall AI

Use this as the normal Tavall AI operating entry point.

## Architecture

- **bootstrap** discovers/validates Tavall agents and AI runtime capability modules;
- **runtime** is a launchable AI/model execution identity such as `NODE_AGENT` or `CHATGPT_WEB`;
- **agent** is reusable behavior/instructions/function requirements and contains no model runtime;
- **runtime capability module** owns actual AI runtime behavior such as distributed provider routing;
- **execution provider** adapts an authorized model/process backend;
- **scheduler agent** describes workload/session placement and recovery only.

## Repository routing

For substantive repository work:

1. Start with `tavall-agent-orchestration`.
2. Apply `tavall-staging-pr-workflow` before mutation/review/acceptance.
3. Use specialized `tavall-agent-*` skills for implementation, architecture, review, reconciliation, E2E, documentation, scheduling, or Builder work.
4. Use `tavall-staging-reconciliation` for topology repair and `tavall-staging-promotion` only at the staging-root promotion boundary.
5. Use repository-owned local CI against exact heads; GitHub workflow YAML is not Tavall's build truth.

Function Catalog owns canonical typed functions, scoped callable views, policy/audit, and MCP projection. Agent metadata requests function names but grants no authority.

Executable capabilities such as Git, Java, Gradle, GitHub CLI, Builder Studio, or browsers remain tools granted by the owning Cloud/runtime authority. Do not manufacture MCP wrappers solely to turn an executable into a function.

Tavall Cloud controls DEVELOPMENT eligibility, jobs, workspace/process/network authority, executables/credentials, resource capacity, local CI execution, and external mutation authorization.

Mutation agents push meaningful checkpoints so branches remain durable distributed state. Staging integration, promotion to `main`, and deployment are separate state transitions and must remain separately evidenced/authorized.
