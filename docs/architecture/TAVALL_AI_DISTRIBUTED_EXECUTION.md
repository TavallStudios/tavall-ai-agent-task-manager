# Tavall AI Distributed Execution Architecture

> **Status:** Active correction layered on the Tavall AI role-runtime transition.

## Purpose

Tavall AI needs a first-class execution layer for bounded AI/model calls across all eligible Tavall AI execution surfaces, including development-node runtimes and web-backed runtimes such as ChatGPT Web.

This responsibility is distinct from generic workload scheduling.

```text
requesting Tavall AI runtime/module
            |
            v
 distributed AI execution module
   capability / health / policy-safe routing
            |
      +-----+--------------------+
      |                          |
      v                          v
 Tavall AI node runtime     Tavall AI web runtime
 Codex / provider process   ChatGPT Web / future web provider
      |                          |
      +------------+-------------+
                   |
                   v
          typed execution result
```

The distributed execution module routes only among execution targets already made eligible by the owning authority. It is not a second scheduler, control plane, credential broker, or infrastructure authority.

## Ownership boundaries

### Tavall Cloud

Tavall Cloud remains the authority beneath distributed AI execution. It owns:

- DEVELOPMENT-node eligibility;
- durable job identity and lifecycle;
- worker/node capacity and reservations;
- workspace leases;
- process, cgroup, sandbox and network isolation;
- credential and executable/tool grants, including GitHub CLI materialization;
- target mutation authorization;
- audit and revocation.

A Tavall AI execution target is never eligible merely because it advertises itself. Cloud or another explicitly authorized host adapter must supply the already-authorized target/lease context.

### Tavall Scheduler

Tavall Scheduler remains generic workload coordination. It may decide where durable work should run and may use Cloud scheduling primitives, but it does not own model/provider semantics, AI capability matching, model fallback, web execution selection, or AI response handling.

The Tavall AI scheduler role therefore coordinates durable worker/session placement only.

### Tavall AI distributed execution

The distributed execution module owns AI-specific routing after authority and placement boundaries are satisfied:

- execution-surface discovery from authorized providers;
- capability matching;
- health/readiness filtering;
- deterministic priority selection;
- bounded retry/failover across eligible targets;
- request/result correlation;
- attempt evidence;
- explicit terminal failure when no eligible target remains.

It does not create Cloud grants, widen mutation scope, infer DEVELOPMENT authority, mint credentials, or bypass a durable job lease.

### Function Catalog

Function Catalog remains the provider-neutral callable-function system: canonical schemas, invocation, narrowed views, policy/audit hooks and MCP projection.

Distributed AI execution is not implemented by manufacturing an MCP wrapper for every CLI or process capability. Functions remain functions; executables remain executables.

## Tavall AI repository layering

Tavall AI follows the same conceptual separation used by the Project Novus runtime/module architecture:

```text
bootstrap
  -> discovers and composes runtimes/modules/providers

runtimes
  -> launchable process identities
  -> node runtime
  -> web runtime
  -> provider-specific runtime adapters such as Codex

modules
  -> loadable behavior/domain capabilities
  -> orchestration
  -> implementation
  -> review
  -> reconciliation
  -> E2E
  -> architecture
  -> documentation
  -> distributed execution
  -> Builder
```

The existing `tavall-ai-agent-core` name is transitional. Core responsibilities should move into explicit bootstrap/composition modules rather than becoming a permanent generic dependency bucket.

Likewise, role modules are modules, not separate AI processes. A Codex/model execution may load and compose multiple role modules inside one authorized runtime.

## Runtime identities

The runtime contract must support at least:

- `NODE_AGENT`: an authorized Tavall AI runtime on a DEVELOPMENT node;
- `CHATGPT_WEB`: a Tavall AI web execution runtime that owns ChatGPT Web conversation/session mechanics without becoming Tavall Cloud's inbound ChatGPT-to-CONTROL adapter.

Later runtimes/providers may be added without changing the distributed execution contract.

Transport may differ between node and web execution, but request identity, capability requirements, attempt evidence and result semantics remain common.

## Distributed execution contract

A distributed request carries only AI execution concerns that are safe to route after authority has already been established:

- execution id;
- durable job id/version or equivalent authority correlation;
- required capabilities;
- preferred execution surfaces when present;
- bounded attempt count;
- task/payload reference;
- opaque authority/lease reference owned by the host adapter.

An execution target carries:

- stable target id;
- execution surface;
- advertised AI capabilities;
- readiness state;
- routing priority;
- provider-specific opaque handle.

The router must not inspect or reinterpret secrets from the opaque handle.

## Routing rules

1. Reject an invalid or empty execution request.
2. Ask registered target providers only for targets authorized for that request.
3. Filter targets that are not ready or do not satisfy every required capability.
4. Honor explicit surface constraints/preferences without silently widening them.
5. Rank deterministically by request preference, provider priority and stable target id.
6. Execute against the highest-ranked eligible target.
7. Retry/fail over only when the provider marks the failure retryable and the request attempt budget remains.
8. Preserve ordered attempt evidence.
9. Stop on success or a non-retryable failure.
10. Return an explicit terminal result when no eligible target remains.

## Scheduler correction

The current scheduler role description overstates its ownership by treating worker/session placement as the Tavall AI distributed execution system.

Correct split:

```text
Tavall AI scheduler role
  -> where should this durable workload/session live?

Tavall AI distributed execution module
  -> which authorized AI execution surface/provider should satisfy this AI call?
```

A scheduler decision may produce or narrow the set of eligible targets. It does not perform provider selection on behalf of the distributed execution module.

## Codex execution provider

The existing `codex-agent-provider` is an execution adapter, not an agent or scheduler. Its responsibility is to turn one already-authorized Tavall AI execution request into a supervised `codex exec` process inside the exact authorized workspace.

During the runtime/module migration it should move under Tavall AI and be renamed toward `CodexExecutionProvider` / `tavall-ai-runtime-codex`.

The process supervisor and Cloud authority boundary remain intact.

## Builder as the first domain-agent acceptance case

Builder is the first serious Tavall domain-agent workload and must be included in this architecture pass rather than treated as an unrelated Project Novus tool.

Existing Builder authoring/validation remains where its Minecraft implementation belongs, including BuildSpec, Sponge schematic artifacts, mock worlds, replay-first simulation, Prismarine/Studio visualization, Mineflayer/FAWE certification and visual evidence.

Tavall AI adds the domain-agent composition layer:

```text
Builder module
  -> Builder-specific instructions and skills
  -> Planner / Terrain / Architecture / Detail / Repair / Visual Critic behavior
  -> generic orchestration/review/E2E modules
  -> distributed AI execution for ambiguous/visual/model calls
  -> Cloud-granted CLIs/functions/workspaces
  -> existing minecraft-bot-builder artifact + validation contracts
```

Builder does not become a production Minecraft runtime and does not duplicate its world/schematic implementation inside Tavall AI.

This makes Builder the acceptance proof that Tavall AI can host a persistent domain module combining skills, specialized tools, distributed model calls, visual feedback, replay artifacts and iterative execution.

## Migration sequence

1. Add the distributed-execution contracts/router as an independent Tavall AI module.
2. Correct scheduler role/docs so it owns placement only.
3. Add node + ChatGPT Web runtime identities to the common runtime model.
4. Refactor the transitional `agent-core` surface into explicit bootstrap/composition modules.
5. Move AI runtime/provider ownership (`agent-runtime`, Codex execution adapter) out of Function Catalog and into Tavall AI while retaining Function Catalog dependencies for callable functions.
6. Add the Tavall AI Builder domain module and bind it to existing Builder skills/artifacts rather than duplicating implementation.
7. Validate node->web fallback, web->node preference, capability filtering, bounded retry, cancellation/revocation behavior and Builder end-to-end delegation on authorized DEVELOPMENT infrastructure.

## Non-goals

- no model execution on non-DEVELOPMENT nodes;
- no direct agent-to-agent SSH/private control plane;
- no replacement for Tavall Scheduler;
- no second Cloud authority implementation;
- no generic browser automation masquerading as ChatGPT Web runtime;
- no duplication of GitHub CLI ownership outside Tavall Cloud;
- no migration of Builder world/schematic production code into the Tavall AI runtime;
- no production deployment implied by source integration.
