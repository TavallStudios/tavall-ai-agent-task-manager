# Tavall Agent and AI Runtime Architecture

> **Status:** Active architecture for Tavall agents loaded by Tavall AI runtimes.

## Purpose

Tavall agents are reusable behavior/instruction/capability packages. **They do not contain AI or a model runtime.** An authorized Tavall AI runtime loads agents and supplies the model/execution surface that performs the work.

The active separation is:

```text
Tavall AI runtime
  -> launchable model/execution identity
  -> owns runtime capability modules/providers
  -> uses Tavall AI bootstrap
       -> discovers agents
       -> discovers runtime capability modules
       -> validates composition

Tavall agent
  -> instructions
  -> requested Function Catalog names
  -> required runtime-module identities
  -> domain/behavior contract
  -> no model runtime
```

## Naming rule

Reusable agents use the `tavall-agent-*` artifact prefix and `org.tavall.agent.*` Java namespace.

Active agents:

- `tavall-agent-scheduler`
- `tavall-agent-orchestration`
- `tavall-agent-implementation`
- `tavall-agent-review`
- `tavall-agent-reconciliation`
- `tavall-agent-e2e`
- `tavall-agent-architecture`
- `tavall-agent-documentation`
- `tavall-agent-builder`

The public bootstrap contract is:

- `TavallAgent`
- `TavallAgentProvider`
- `TavallAgentRegistry`
- `TavallAgentKind`
- `TavallAgentCapability`
- `TavallAgentInstructions`

The obsolete `tavall-ai-agent-core`, `tavall-ai-agent-*`, and `tavall-ai-module-builder` artifact families are not active compatibility layers. They are removed.

The `AI` prefix remains where the component actually owns model/runtime semantics, for example `tavall-ai-runtime` and `tavall-ai-runtime-distributed-execution`.

## System layering

```text
ChatGPT / automation / operator
              |
              v
      durable Tavall AI job
              |
              v
       Tavall Cloud authority
 placement / lease / tools / audit
              |
              v
       Tavall AI runtime
       +------+-------+
       |              |
       v              v
  NODE_AGENT      CHATGPT_WEB
       |              |
       +------+-------+
              |
              v
       tavall-ai-bootstrap
       |               |
       v               v
 Tavall agents   runtime capability modules
       |               |
       +-------+-------+
               |
               v
       execution provider
```

### Tavall Cloud

Tavall Cloud remains the authority beneath Tavall AI execution. It owns explicit DEVELOPMENT eligibility, durable jobs, capacity/workspace leases, process/sandbox/network authority, executable/tool/credential grants, mutation authorization, revocation, and fencing.

The ordinary Tavall Cloud Node Agent remains non-AI infrastructure. Hosting an authorized Tavall AI runtime does not turn the Cloud node agent into a model runtime.

### Bootstrap

`tavall-ai-bootstrap` is reusable composition infrastructure. It discovers both Tavall agents and Tavall AI runtime capability modules and validates the resulting graph before host execution.

A `TavallAgentProvider` publishes one `TavallAgent`. A `TavallAIModuleProvider` publishes one actual runtime capability module. Agent metadata may declare required runtime-module IDs; the parent runtime must satisfy those requirements before execution begins.

Bootstrap does not execute a model, schedule Tavall work, or grant Cloud authority.

### Runtimes

A runtime is a launchable Tavall AI execution identity.

Current identities:

- `NODE_AGENT`: model-backed Tavall execution hosted on an authorized DEVELOPMENT node;
- `CHATGPT_WEB`: model-backed Tavall execution through an authorized ChatGPT Web host/session.

Both compose the same installed agent and runtime-module universe through `TavallAIRuntimeContext`. Their transports differ; agent semantics do not.

`CHATGPT_WEB` remains distinct from Tavall Cloud's inbound ChatGPT-to-CONTROL MCP adapter. One is an execution surface; the other exposes Cloud operations to ChatGPT.

## Agents

Agents package reusable behavior and tool requirements. A single model session may load or coordinate several agents and may execute multiple logical subagents. Requiring another agent does not imply another machine, model process, or Cloud worker.

Agent metadata describes intended requirements only. It never grants callable functions, shell access, credentials, Cloud authority, repository write authority, or deployment authority.

### Scheduler agent

The scheduler answers:

> Where should this durable workload or top-level session live?

It coordinates eligible worker/session placement, ownership, and recovery using Tavall Cloud/Tavall Scheduler authority where appropriate. It does not contain a model runtime and does not own provider/model/web routing.

### Orchestration agent

The orchestration agent coordinates the smallest useful set of specialized agents/subagents inside the owning model session. It may request runtime subagent/distributed-job functions but does not itself become the model/provider runtime.

### Work agents

Implementation, review, reconciliation, E2E, architecture, and documentation agents package their focused behavior and Function Catalog requirements. Repository mutation and acceptance authority remain external to the package.

### Builder agent

`tavall-agent-builder` composes Planner, Terrain, Architecture, Detail, Repair, and Visual Critic behavior around the Project Novus `minecraft-bot-builder` platform.

It recognizes Builder artifacts such as BuildSpec, Sponge `.schem`, Tavall replay, visual evidence, and WorldBakeManifest without copying Builder geometry/rendering/replay/FAWE/Mineflayer implementation into Tavall AI.

Builder declares the runtime requirement `distributed-execution` for model/vision/repair calls. It does **not** import the distributed AI runtime module. The parent runtime validates and supplies that capability.

Builder can also request a typed Builder Studio simulation through `BuilderStudioSimulationRunner`. The runtime/Cloud host supplies the trusted executable/process boundary. The agent may pass only validated Studio arguments and workspace-contained artifact/evidence paths; it cannot submit arbitrary shell fragments.

## Distributed AI execution

`tavall-ai-runtime-distributed-execution` is an AI **runtime capability module**, not an agent.

It answers:

> Which already-authorized AI execution surface/provider should satisfy this bounded model call?

It owns authorized target-provider discovery, capability/readiness filtering, explicit allowed/preferred surfaces, deterministic priority, bounded retry/failover, ordered attempt evidence, and explicit no-target/terminal results.

Its first-class execution surfaces are `NODE_AGENT` and `CHATGPT_WEB`.

It receives already-authorized targets from the host/provider boundary. It does not infer DEVELOPMENT authority, mint Cloud grants, discover arbitrary infrastructure, or widen an allowed target set.

Tavall Scheduler remains generic workload coordination; distributed execution remains model/provider routing.

## Execution providers

Execution providers adapt an authorized AI execution request to a concrete model/process backend.

The Function Catalog `agent-runtime` and `codex-agent-provider` modules are ownership drift. Their runtime/provider responsibilities migrate to Tavall AI; Function Catalog remains the typed callable-function/schema/invocation/policy/audit/MCP system.

The Cloud-owned process isolation/supervision boundary remains authoritative even when Tavall AI owns the provider adapter.

## Function Catalog integration

Agents declare requested callable function names. Function Catalog owns the real function implementations and the narrowed execution-specific view.

```text
Tavall agent
  -> requested function names
  -> Function Catalog policy/view
  -> typed Java function implementation
  -> provider-neutral MCP projection when authorized
```

Function Catalog remains responsible for canonical function definitions/schemas, invocation, policy/audit hooks, scoped views, structured results, and MCP projection.

Agent packages do not hand-maintain duplicate MCP schemas and do not gain authority by naming a function.

## Executable capabilities

Functions and executables are different capability types.

Git, GitHub CLI, Java, Gradle, Builder Studio, browser/runtime helpers, and other executables may be granted/materialized by Tavall Cloud or another authoritative runtime host. An agent describes how to use a granted capability; it does not convert every executable operation into an MCP function.

## Local verification

Repository verification logic lives locally in the repository. Tavall execution workers run the canonical local entrypoint against an exact head and preserve evidence tied to that head.

For Tavall AI:

```text
scripts/ci/verify
scripts/ci/verify.cmd
```

Those entrypoints run the actual Gradle checks and stage the runtime/plugin distribution. Stale GitHub Actions CI has been removed. GitHub may receive statuses/evidence later, but it is not the source of build logic.

## Durable progress

Mutation agents commit and push meaningful checkpoints while working. A PR branch is durable distributed task state. Worker loss should be recoverable by another eligible execution fetching the latest pushed checkpoint and explicit handoff metadata.

Staging PR ancestry remains the integration/future-tree boundary for repository work; distributed AI execution does not replace the Git/PR workflow.

## Authority rule

- Tavall Cloud controls development placement, leases, process/network/tool/credential authority, executable grants, and external mutation authorization.
- Function Catalog controls callable function definitions and execution-specific views.
- Tavall AI runtimes own model/runtime composition and runtime capability modules/providers.
- Tavall AI bootstrap discovers and validates agents/modules.
- Tavall agents package behavior and requirements only.
- Tavall Scheduler controls generic workload coordination where used.
- Git/PR ownership, staging metadata, review, and reconciliation control repository mutation coordination.
