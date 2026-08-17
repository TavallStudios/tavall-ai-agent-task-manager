# Tavall AI Agent Role Architecture

> **Status:** Active architecture for the `tavall-ai` role layer.

## Purpose

Tavall AI agents are intentionally thin. A deployable `tavall-ai-agent-ROLE` module is primarily a home for role instructions, requested Function Catalog capabilities, and role policy metadata. It is not a separate model implementation or a requirement for a separate operating-system process.

## Layering

```text
ChatGPT / automation / Codex
          |
          v
  scheduler role
  distributed placement
          |
          v
 Tavall AI Node Agent
          |
          v
   Codex session
          |
          v
 orchestration role
          |
    +-----+------+----------------+
    |            |                |
 implementation review       documentation ...
   subagent     subagent        subagent
```

### Tavall Node Agent

The ordinary Tavall Node Agent is non-AI Java infrastructure installed by the normal Tavall node installer. It owns normal node identity, CONTROL transport, service/process/sandbox/workspace infrastructure, networking, logging, and resource reporting. It must not grow model prompts, Codex role policy, or AI-specific orchestration behavior.

### Tavall AI Node Agent

A Tavall AI Node Agent is separate deployable software for explicitly authorized AI-capable DEVELOPMENT nodes. It composes Function Catalog agent/runtime providers, installed Tavall AI role modules, and the workspace/process-isolation capabilities granted by Tavall Cloud.

### Scheduler role

`tavall-ai-agent-scheduler` performs distributed scheduling. It decides which eligible worker and top-level session should own an executable durable job. Tavall Cloud remains authoritative for DEVELOPMENT-only placement, capacity reservations, job lifecycle, workspace leases, and process isolation.

The scheduler does not implement code, perform review, or run E2E itself.

### Orchestration role

`tavall-ai-agent-orchestration` coordinates specialized roles inside one already-placed Codex session.

A single Codex session can and should use multiple Tavall agents/subagents when they can safely share the owning workspace and resource envelope. Needing another role is not by itself a reason to allocate another machine or top-level session.

The orchestration role requests another distributed scheduler job only when a real boundary exists, such as:

- a required capability exists only on another worker;
- Minecraft/browser/runtime E2E needs a dedicated host;
- CPU/RAM/process limits make the current worker unsuitable;
- a distinct workspace or process-isolation lease is required;
- safe parallelism exists across independent acceptance units.

## Deployable role modules

- `tavall-ai-agent-scheduler`
- `tavall-ai-agent-orchestration`
- `tavall-ai-agent-implementation`
- `tavall-ai-agent-review`
- `tavall-ai-agent-reconciliation`
- `tavall-ai-agent-e2e`
- `tavall-ai-agent-architecture`
- `tavall-ai-agent-documentation`

Every module exposes `TavallAIAgentRoleProvider` through Java `ServiceLoader`, allowing a Tavall AI Node Agent to install only the roles appropriate for that node.

## Function Catalog integration

Role modules do not own tool implementations. They declare required and optional Function Catalog function names.

```text
role module
  -> requested function names
  -> authoritative Function Catalog policy view
  -> execution-specific narrowed view
  -> Java/application functions
```

Java application code should expose useful typed capabilities through Function Catalog registration/annotations. The Function Catalog remains responsible for schemas, invocation, policy hooks, audit behavior, structured results, and provider-neutral tool projection.

Missing required functions are an installation/execution readiness problem, not permission for a role to synthesize arbitrary shell access.

## Local CI

CI is deterministic infrastructure, not an AI role.

Each repository should expose a canonical local verification entrypoint, such as `scripts/ci/verify`. Tavall Cloud workers execute that entrypoint against an exact commit inside an authorized isolated workspace/sandbox. The result is bound to the exact head and may later be published to GitHub as a Check/status.

GitHub Actions is not the default execution plane for Tavall CI. GitHub remains useful for pull requests, review, visible checks, releases, and external audit history.

## Durable progress

Mutation roles commit and push meaningful checkpoints while working. A PR branch is durable distributed task state. Worker loss should be recoverable by scheduling another eligible worker, fetching the latest pushed checkpoint, and continuing from explicit handoff metadata.

## Authority rule

Role metadata describes intended capability. It never grants authority.

- Function Catalog controls callable function views.
- Tavall Cloud controls development-node placement, machine/resource authority, workspace leases, process isolation, and external operation authorization.
- Git/PR ownership and Tavall reconciliation markers control repository mutation coordination.
