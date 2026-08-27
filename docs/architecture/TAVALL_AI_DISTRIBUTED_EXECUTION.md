# Tavall AI Distributed Execution Architecture

> **Status:** Active Tavall AI runtime capability.

## Purpose

Tavall AI needs a first-class execution layer for bounded model calls across already-authorized execution surfaces, including DEVELOPMENT-node runtimes and web-backed runtimes such as ChatGPT Web.

This is an **AI runtime capability**, not a Tavall agent and not generic workload scheduling.

```text
Tavall agent / runtime work
            |
            v
 parent Tavall AI runtime
            |
            v
 tavall-ai-runtime-distributed-execution
 capability / readiness / bounded routing
       +----+-------------------+
       |                        |
       v                        v
 NODE_AGENT target         CHATGPT_WEB target
 provider adapter          provider adapter
       |                        |
       +-----------+------------+
                   |
                   v
          typed execution result
```

## Ownership boundaries

### Tavall Cloud

Tavall Cloud owns DEVELOPMENT eligibility, durable job identity/lifecycle, capacity and workspace leases, process/sandbox/network authority, executable/tool/credential grants, mutation authorization, audit, revocation, and fencing.

A target is not eligible because it advertises itself. The target-provider/host boundary supplies only targets already authorized for the request.

### Tavall Scheduler and scheduler agent

Tavall Scheduler remains generic workload coordination. The `tavall-agent-scheduler` package describes placement/recovery behavior for durable work and top-level sessions. Neither owns model/provider semantics, model fallback, or web-vs-node routing.

### Distributed execution runtime module

`tavall-ai-runtime-distributed-execution` owns AI-specific routing after authority/placement boundaries are satisfied:

- target-provider discovery from authorized providers;
- capability matching;
- health/readiness filtering;
- explicit allowed/preferred surface handling;
- deterministic target priority;
- bounded retry/failover;
- request/result correlation;
- ordered attempt evidence;
- explicit terminal results when no eligible target remains.

It does not create Cloud grants, widen mutation scope, infer DEVELOPMENT authority, mint credentials, or bypass a durable lease.

### Function Catalog

Function Catalog owns typed callable functions: canonical schemas, invocation, narrowed views, policy/audit hooks, structured results, and MCP projection.

Distributed AI execution is not implemented by manufacturing MCP wrappers around every executable/process capability. Functions remain functions; executables remain executables.

## Repository layering

```text
tavall-ai-runtime
  -> tavall-ai-bootstrap
       -> Tavall agents (`tavall-agent-*`)
       -> runtime capability modules
  -> tavall-ai-runtime-distributed-execution
  -> execution-provider adapters
```

Tavall agents contain instructions/tool requirements and may declare required runtime-module IDs. They do not import or embed the model runtime. The parent runtime validates that required runtime modules are installed before host execution.

The removed `tavall-ai-agent-core` compatibility surface is not part of the active architecture.

## Runtime identities

The common runtime model currently supports:

- `NODE_AGENT`: authorized Tavall AI execution on a DEVELOPMENT node;
- `CHATGPT_WEB`: authorized Tavall AI execution through a ChatGPT Web host/session.

Transport may differ, but request identity, capability requirements, attempt evidence, and result semantics remain common.

`CHATGPT_WEB` remains separate from Tavall Cloud's inbound ChatGPT-to-CONTROL MCP adapter.

## Distributed execution contract

A request carries AI execution concerns safe to route after authority is established:

- execution id;
- durable job/version or equivalent correlation;
- required capabilities;
- allowed/preferred execution surfaces;
- bounded attempt count;
- task/payload reference;
- opaque authority/lease reference owned by the host adapter.

A target carries:

- stable target id;
- execution surface;
- advertised AI capabilities;
- readiness state;
- routing priority;
- provider-specific opaque handle.

The router does not inspect/reinterpret secrets from opaque handles.

## Routing rules

1. Reject invalid/empty requests.
2. Ask registered providers only for targets authorized for that request.
3. Reject targets that are unready or missing required capabilities.
4. Honor explicit surface constraints without widening them.
5. Rank deterministically by request preference, provider priority, and stable target id.
6. Execute against the highest-ranked eligible target.
7. Retry/fail over only for provider-declared retryable failures while budget remains.
8. Preserve ordered attempt evidence.
9. Stop on success or terminal failure.
10. Return explicit no-target/attempt-exhausted results rather than silently falling back outside policy.

## Scheduler split

```text
tavall-agent-scheduler
  -> where should this durable workload/top-level session live?

tavall-ai-runtime-distributed-execution
  -> which already-authorized AI target/provider satisfies this bounded model call?
```

A placement decision may narrow the eligible target set. It does not replace provider selection.

## Codex execution provider

The Function Catalog `codex-agent-provider` is semantically an execution adapter, not an agent definition or scheduler. Its runtime/provider responsibility should move into Tavall AI, toward a `tavall-ai-runtime-codex` / `CodexExecutionProvider` boundary.

Cloud process isolation/supervision remains authoritative around that provider.

## Builder acceptance case

`tavall-agent-builder` is the first serious domain-agent acceptance case.

Builder keeps Minecraft implementation in Project Novus: BuildSpec, schematics, replay/mock simulation, Builder Studio/Prismarine rendering, Mineflayer/FAWE certification, and visual evidence.

The Tavall agent adds behavior/composition only:

```text
tavall-agent-builder
  -> Planner / Terrain / Architecture / Detail / Repair / Visual Critic behavior
  -> existing Tavall agent orchestration/review/E2E behavior as needed
  -> declares runtime requirement: distributed-execution
  -> typed Builder Studio simulation runner
  -> existing Project Novus Builder artifacts/acceptance
```

The Builder Studio runner is an executable capability supplied by the authorized runtime/Cloud host, not an MCP function and not an AI runtime. It accepts typed arguments, workspace-contained artifact/evidence paths, and returns session/status/evidence references. It never grants production-world mutation authority.

## Migration sequence

1. Keep distributed execution runtime-owned under `tavall-ai-runtime-distributed-execution`.
2. Keep scheduler behavior in `tavall-agent-scheduler`; no AI runtime inside the agent.
3. Keep node + ChatGPT Web as parent runtime identities.
4. Remove the old AI-agent core/namespace and load `tavall-agent-*` through bootstrap.
5. Move actual AI runtime/provider ownership (`agent-runtime`, Codex adapter) out of Function Catalog into Tavall AI while Function Catalog retains callable-function/MCP ownership.
6. Validate exact-head node/web routing and Builder Studio/live Builder acceptance on authorized DEVELOPMENT execution surfaces.

## Non-goals

- no model execution on non-DEVELOPMENT nodes;
- no direct agent-to-agent SSH/private control plane;
- no replacement for Tavall Scheduler;
- no second Cloud authority implementation;
- no duplication of Builder Minecraft implementation inside Tavall AI;
- no arbitrary shell field in Builder Studio execution;
- no production deployment implied by source integration.
