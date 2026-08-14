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
 runtime target            runtime target
       |                        |
       +-----------+------------+
                   |
                   v
      tavall-ai-runtime-model-execution
                   |
                   v
          selected model provider
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

### Model execution runtime

`tavall-ai-runtime-model-execution` owns the actual single-provider model invocation after distributed routing has selected/authorized an execution target.

It combines a canonical non-AI `TavallAgent` with a selected provider id, resolves the authoritative Function Catalog policy view, intersects it with the agent's requested function names, enforces tool/delegation/time budgets, invokes the provider, records observed Function Catalog tool calls, and returns structured results.

Distributed execution and model execution intentionally use distinct contracts: one routes among authorized targets; the other invokes one selected model provider.

### Function Catalog

Function Catalog owns typed callable functions: canonical schemas, invocation, narrowed views, policy/audit hooks, structured results, and MCP projection.

Distributed/model execution is not implemented by manufacturing MCP wrappers around every executable/process capability. Functions remain functions; executables remain executables.

## Repository layering

```text
tavall-ai-runtime
  -> tavall-ai-bootstrap
       -> Tavall agents (`tavall-agent-*`)
       -> runtime capability modules
  -> tavall-ai-runtime-distributed-execution
  -> tavall-ai-runtime-model-execution
  -> tavall-ai-runtime-codex
```

Tavall agents contain instructions/tool requirements and may declare required runtime-module IDs. They do not import or embed the model runtime. The parent runtime validates required runtime modules before host execution.

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
  -> which already-authorized AI target should satisfy this bounded model call?

tavall-ai-runtime-model-execution
  -> invoke the selected provider with the scoped Function Catalog view and model budget
```

A placement decision may narrow the eligible target set. It does not replace distributed target selection or model-provider invocation.

## Codex execution provider

`tavall-ai-runtime-codex` now owns the Codex provider adapter previously held in Function Catalog.

`CodexModelProvider` preserves the fixed ephemeral CLI shape, sandbox selection, bounded output, environment allowlist, temporary-run cleanup, and Git-root validation. Tavall AI owns the provider adapter, while the authorized host supplies `CodexWorkspaceResolver` and `CodexProcessIsolationSupervisor` so workspace/process authority remains with Tavall Cloud or another explicit runtime host.

Function Catalog PR #13 removes the obsolete `agent-runtime` / `codex-agent-provider` copies only after this replacement passes its owning exact-head/runtime acceptance.

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

## Migration status

Completed in source on this stack:

1. distributed execution is runtime-owned under `tavall-ai-runtime-distributed-execution`;
2. scheduler behavior is in non-AI `tavall-agent-scheduler`;
3. node + ChatGPT Web remain parent runtime identities;
4. the old AI-agent core/namespace is removed and `tavall-agent-*` loads through bootstrap;
5. provider-neutral model execution is now `tavall-ai-runtime-model-execution`;
6. Codex provider ownership is now `tavall-ai-runtime-codex`;
7. Tavall AI source control consumes Function Catalog only through `org.tavall:ai-core`.

Still required before calling the migration operationally accepted:

- exact-head Java 25 local verification;
- DEVELOPMENT Codex model-provider acceptance through the host-supplied process supervisor/workspace lease;
- node/web distributed-routing acceptance;
- Builder Studio/live Builder acceptance;
- Function Catalog #13 removal reconciliation after the Tavall AI replacement is proven.

## Non-goals

- no model execution on non-DEVELOPMENT nodes;
- no direct agent-to-agent SSH/private control plane;
- no replacement for Tavall Scheduler;
- no second Cloud authority implementation;
- no duplication of Builder Minecraft implementation inside Tavall AI;
- no arbitrary shell field in Builder Studio execution;
- no production deployment implied by source integration.
