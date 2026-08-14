# Tavall AI Runtime and Module Architecture

> **Status:** Active architecture for the `tavall-ai` execution system.

## Purpose

Tavall AI is the execution/composition layer for model-backed Tavall work. Its roles and domain packages are modules loaded into actual model/runtime executions; they are not independent AIs or one-daemon-per-role services.

The repository is moving to the same conceptual separation used by Project Novus:

```text
bootstrap
  -> discovers/composes installed modules

runtimes
  -> launchable process identities

modules
  -> loadable role/domain capabilities

execution providers
  -> concrete model/process adapters
```

The transitional `tavall-ai-agent-core` name remains only while the role-runtime PR stack is migrated. Generic composition belongs in explicit bootstrap modules rather than a permanent `core` junk drawer.

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
      Tavall AI runtime host
       +------+-------+
       |              |
       v              v
  NODE_AGENT      CHATGPT_WEB
       |              |
       +------+-------+
              |
              v
   bootstrap module graph
       |              |
       v              v
 role modules      domain modules
       |              |
       +------+-------+
              |
              v
 execution provider / distributed execution
```

### Tavall Cloud

Tavall Cloud remains the authority beneath Tavall AI execution. It owns:

- explicit DEVELOPMENT-node eligibility;
- durable job lifecycle and audit;
- worker/capacity reservation;
- workspace leases;
- process/cgroup/sandbox/network authority;
- executable/tool and credential grants, including GitHub CLI;
- target mutation authorization;
- revocation and fencing.

The ordinary Tavall Node Agent remains non-AI infrastructure. It may host/control the authorized transport without absorbing model prompts, Tavall AI modules, or provider behavior.

### Bootstrap

`tavall-ai-bootstrap` owns provider-neutral module composition/discovery.

A `TavallAIModuleProvider` publishes one `TavallAIModule`. The registry rejects duplicate module identities and missing module dependencies before runtime execution.

Bootstrap does not become a model runtime, scheduler, Cloud authority, or catch-all home for unrelated application behavior.

### Runtimes

A runtime is a launchable Tavall AI process identity.

Current runtime identities:

- `NODE_AGENT`: Tavall AI execution hosted on an authorized DEVELOPMENT node;
- `CHATGPT_WEB`: Tavall AI execution through an authorized ChatGPT Web session/conversation host.

Both runtimes compose the same installed role/module universe through `TavallAIRuntimeContext`. Their transports differ, but module semantics do not.

`CHATGPT_WEB` is intentionally separate from Tavall Cloud's inbound ChatGPT-to-CONTROL MCP adapter. The inbound adapter exposes Cloud operations to ChatGPT; the Tavall AI web runtime executes Tavall AI work through a web-backed model/session surface.

### Role modules

Role modules supply reusable execution behavior such as:

- scheduler;
- orchestration;
- implementation;
- review;
- reconciliation;
- E2E;
- architecture;
- documentation.

A single Codex/model session may compose several role modules or subagents. Requiring another role is not by itself a reason to allocate another process or machine.

The current `tavall-ai-agent-*` artifact names are transitional compatibility names. Their semantic category is **role module**.

### Domain modules

Domain modules compose Tavall AI around a durable product/domain workflow.

The first concrete domain module is `tavall-ai-module-builder`.

Builder composes Planner, Terrain, Architecture, Detail, Repair and Visual Critic behavior around the existing Project Novus `minecraft-bot-builder` artifact/validation platform. Tavall AI does not duplicate Builder geometry, palettes, schematics, replay, Studio, FAWE, Mineflayer or world-foundry implementation.

Builder depends on the distributed-execution module for model/vision/repair calls that may route across authorized node or web runtimes.

## Scheduler versus distributed AI execution

These are deliberately separate.

### Scheduler role

The scheduler answers:

> Where should this durable workload/top-level session live?

It coordinates eligible worker/session placement, recovery and ownership using Tavall Cloud/Tavall Scheduler authority as appropriate.

It does not own AI provider/model/web routing.

### Distributed execution module

`tavall-ai-module-distributed-execution` answers:

> Which already-authorized AI execution surface/provider should satisfy this bounded AI call?

It owns:

- authorized target-provider discovery;
- required-capability filtering;
- readiness filtering;
- explicit allowed/preferred execution surfaces;
- deterministic target priority;
- bounded retry/failover;
- ordered attempt evidence;
- explicit no-target/terminal failure results.

Its first-class surfaces are `NODE_AGENT` and `CHATGPT_WEB`.

The module receives already-authorized targets from host/provider adapters. It does not infer DEVELOPMENT authority, mint Cloud grants, discover arbitrary infrastructure, or widen an allowed surface set.

Tavall Scheduler remains generic workload coordination. It should not absorb model/provider semantics merely because an AI job uses it for placement.

## Execution providers

An execution provider adapts one authorized execution request to a concrete model/process backend.

The current Function Catalog `codex-agent-provider` is actually this kind of component: it validates the leased Git workspace and launches supervised `codex exec`. It is not an agent definition or scheduler.

During the ownership migration it should move into Tavall AI and be renamed toward `tavall-ai-runtime-codex` / `CodexExecutionProvider`.

The Cloud-owned process isolation supervisor boundary remains authoritative.

## Function Catalog integration

Function Catalog owns **callable functions**, not Tavall AI runtimes/modules and not every executable the model may use.

```text
Tavall AI role/domain module
  -> requested callable function names
  -> authoritative Function Catalog policy view
  -> execution-specific narrowed view
  -> Java/application functions
```

Function Catalog remains responsible for:

- canonical function definitions and schemas;
- invocation;
- narrowed views;
- policy/audit hooks;
- structured/rich results;
- provider-neutral MCP projection.

The current Function Catalog modules named `agent-runtime` and `codex-agent-provider` are transitional ownership drift and should migrate into Tavall AI. The existing Function Catalog `ai-core` should be narrowed/renamed around Function Catalog concerns rather than treated as the Tavall AI core runtime.

## Executable/CLI capabilities

Functions and executables are different capability types.

Git, GitHub CLI, Java, Gradle, browser/runtime helpers and other CLIs may be granted/materialized into an authorized Tavall AI execution by Tavall Cloud. Tavall AI modules describe how to use the capabilities they receive; they do not automatically turn every CLI operation into an MCP function.

A child/subagent execution may receive only the capabilities the owning authority/runtime permits it to inherit or delegate.

## Local CI

CI is deterministic infrastructure, not an AI role.

Each repository should expose a canonical local verification entrypoint such as `scripts/ci/verify`. Tavall Cloud workers execute it against an exact commit inside an authorized workspace/sandbox. The result is tied to the exact head and may later be published to GitHub as a Check/status.

GitHub Actions is not the default Tavall execution plane.

## Durable progress

Mutation roles commit and push meaningful checkpoints while working. A PR branch is durable distributed task state. Worker loss should be recoverable by another eligible execution fetching the latest pushed checkpoint and continuing from explicit handoff metadata.

Staging PR ancestry remains the integration/future-tree boundary for repository work; distributed AI execution does not replace the Git/PR workflow.

## Authority rule

Runtime/module metadata describes intended behavior and capability requirements. It never grants authority.

- Tavall Cloud controls development placement, leases, process/network/tool/credential authority and external mutation authorization.
- Function Catalog controls callable function views.
- Tavall AI bootstrap/runtimes/modules control model-execution composition.
- Tavall Scheduler controls generic workload coordination where used.
- Git/PR ownership and reconciliation/staging markers control repository mutation coordination.
