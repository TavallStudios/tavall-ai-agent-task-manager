> **AI-operable, human-legible.**

# Tavall AI

This repository is transitioning from the historical AgentTaskManager runtime into the thin Tavall AI role layer.

The active build owns independently deployable `tavall-ai-agent-ROLE` modules: role instructions, requested Function Catalog capabilities, and policy metadata used by Tavall AI Node Agent implementations and Codex sessions.

The historical AgentTaskManager modules remain in the repository only as migration/reference inputs and are no longer included by `settings.gradle.kts`. See [AgentTaskManager Deprecation](docs/AGENT_TASK_MANAGER_DEPRECATION.md).

## Architecture

```text
ChatGPT / automations / Codex
           |
           v
 tavall-ai-agent-scheduler
   distributed placement
           |
           v
   Tavall AI Node Agent
           |
           v
      Codex session
           |
           v
 tavall-ai-agent-orchestration
           |
   +-------+--------+---------+
   |       |        |         |
 implementation  review  documentation  ...
   subagent      subagent     subagent
```

A single Codex session may use multiple specialized Tavall agents/subagents. The scheduler chooses the owning worker/top-level session; the orchestration role coordinates specialized agents inside that session. Another distributed session is requested only when machine capability, resource pressure, process/workspace isolation, dedicated E2E infrastructure, or safe independent parallelism requires it.

Tavall does not recreate the model runtime. Function Catalog supplies typed functions and provider/runtime primitives; Tavall Cloud supplies authoritative DEVELOPMENT-node placement, durable AI-job/capability state, resource/workspace/process authority, and local CI infrastructure.

## Active modules

- `tavall-ai-agent-core` - role metadata, instruction loading, and ServiceLoader discovery.
- `tavall-ai-agent-scheduler` - distributed worker/top-level-session selection only.
- `tavall-ai-agent-orchestration` - same-session specialized agent/subagent coordination.
- `tavall-ai-agent-implementation` - bounded implementation, tests, checkpoint commits/pushes, local CI.
- `tavall-ai-agent-review` - independent exact-head review and evidence assessment.
- `tavall-ai-agent-reconciliation` - PR graph/current-main/migration/ownership reconciliation.
- `tavall-ai-agent-e2e` - realistic exact-head development runtime validation and evidence.
- `tavall-ai-agent-architecture` - approved broad structural migrations.
- `tavall-ai-agent-documentation` - owning technical/progress/evidence documentation.

Each role module registers `TavallAIAgentRoleProvider` with Java `ServiceLoader`, so AI-capable nodes can install only the roles they should host.

## Function Catalog

Role modules do not implement their own tool framework. Each role declares required and optional Function Catalog function names. Java/application modules expose typed operations through Function Catalog registration/annotations, and the execution runtime narrows the authoritative catalog view to the role/job capability set.

Role metadata is not authority. Tavall Cloud and Function Catalog continue to fail closed at their own boundaries.

## Local CI

CI is deterministic infrastructure rather than an AI role.

Repository verification starts through:

```bash
bash scripts/ci/verify
```

or on Windows:

```text
scripts\ci\verify.cmd
```

The intended distributed flow is for Tavall Cloud to run repository-owned CI entrypoints against an exact commit in an authorized isolated workspace/sandbox, then publish the resulting exact-head evidence to GitHub Checks/statuses. GitHub Actions is not the default Tavall CI executor.

## Documents

- [Tavall AI Agent Role Architecture](docs/architecture/TAVALL_AI_AGENT_ROLES.md)
- [AgentTaskManager Deprecation](docs/AGENT_TASK_MANAGER_DEPRECATION.md)
- [Earlier Tavall AI / Open Harness handoff](docs/TAVALL_AI_OPEN_HARNESS_HANDOFF.md) - historical design input; newer role/Cloud boundaries take precedence where they differ.
