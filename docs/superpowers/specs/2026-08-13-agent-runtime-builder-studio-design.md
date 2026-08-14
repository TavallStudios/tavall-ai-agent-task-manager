# Tavall Agent Runtime + Builder Studio Design

> Approved correction from the owner on 2026-08-13. This refines PR #8 without reopening the already-approved distributed-execution architecture.

## Naming and ontology

Tavall agent packages are **not AI runtimes**. They contain behavior, instructions, function/tool requirements, domain contracts, and composition metadata. The actual AI/model lives in the runtime/provider layer that loads them.

Therefore agent artifacts use the `tavall-agent-*` prefix, not `tavall-ai-agent-*` and not `tavall-ai-module-*`.

Canonical agent artifacts:

- `tavall-agent-scheduler`
- `tavall-agent-orchestration`
- `tavall-agent-implementation`
- `tavall-agent-review`
- `tavall-agent-reconciliation`
- `tavall-agent-e2e`
- `tavall-agent-architecture`
- `tavall-agent-documentation`
- `tavall-agent-builder`

Java public types follow the same ontology where practical:

- `TavallAgent`
- `TavallAgentProvider`
- `TavallAgentRegistry`
- `TavallAgentKind`
- `TavallAgentCapability`
- `TavallAgentInstructions`

The word `AI` is reserved for actual model/runtime/execution concerns.

## Runtime/bootstrap hierarchy

Runtime modules are the parent execution layer. Bootstrap is reusable composition infrastructure underneath them.

```text
Tavall AI runtime
  -> Tavall AI bootstrap
      -> installed Tavall agents
      -> runtime capability modules
      -> execution providers
```

`tavall-ai-bootstrap` owns agent/module discovery and graph validation. It does not execute models by itself.

The transitional `tavall-ai-agent-core` module is removed. Its role/agent contracts move into `tavall-ai-bootstrap`.

`tavall-ai-runtime` remains the parent executable distribution and depends on bootstrap plus the runtime-specific capability modules/providers it needs.

## Distributed execution

Distributed model/provider routing is a runtime concern, not an agent.

Rename:

- `tavall-ai-module-distributed-execution` -> `tavall-ai-runtime-distributed-execution`

It remains responsible for already-authorized target discovery, AI capability filtering, node/web execution-surface selection, bounded retry/failover, and attempt evidence.

The scheduler agent remains generic workload/session placement and recovery. It may consume Tavall Scheduler/Tavall Cloud state but does not contain AI or provider-routing logic.

## Builder agent

Rename:

- `tavall-ai-module-builder` -> `tavall-agent-builder`

The Builder agent is a domain agent with no embedded model runtime. It composes the existing Builder behaviors:

- Planner
- Terrain
- Architecture
- Detail
- Repair
- Visual Critic

It consumes the shared Project Novus `minecraft-builder` skill and existing Builder artifact contracts rather than duplicating them.

## Builder Studio simulation execution

The Builder agent must be able to run simulations in Tavall Builder Studio.

Current Studio is Electron/UI driven and exposes dialog-based open/seek behavior but no deterministic external simulation launch contract. The new contract adds an agent-addressable launch mode while preserving the same Studio renderer/replay implementation.

### Studio launch request

A Studio simulation request carries:

- artifact/replay path;
- optional world id;
- playback speed;
- autoplay flag;
- optional initial/final tick bounds;
- visible entity cap when relevant;
- optional evidence output directory;
- visible vs headless/capture mode where supported;
- durable Builder job/execution correlation id.

The request may only refer to paths inside the authorized Builder workspace/artifact boundary.

### Studio execution modes

1. **Visible simulation**
   - launches the Windows/Electron Builder Studio;
   - opens the requested BuildSpec/schematic/replay/world artifact;
   - selects the requested replay world/tick/speed;
   - optionally autoplays;
   - is intended for human-observable Builder work and visual review.

2. **Evidence simulation**
   - uses the same Studio scene/replay pipeline without requiring human dialogs;
   - captures deterministic visual/session evidence where the Studio/runtime supports it;
   - returns artifact references rather than raw UI state.

The Builder agent should normally use replay/mock simulation for iteration and reserve live Paper/FAWE/Mineflayer certification for the later existing acceptance boundary.

### Studio executable boundary

Project Novus Builder Studio owns the executable/CLI/API used to launch simulations. Tavall Agent Builder does not embed Electron, Prismarine Viewer, Minecraft assets, replay implementation, or browser automation.

Tavall Agent Builder receives an authorized executable capability from the runtime/Cloud workspace and invokes the Studio simulation contract through a narrow runner/provider interface.

The runner returns:

- session id;
- execution status;
- opened artifact kind/path reference;
- replay world/tick range when applicable;
- evidence references;
- bounded stdout/stderr or diagnostic reference;
- exit/failure classification.

## Project Novus Studio changes

Builder Studio gains a deterministic non-dialog launch surface, initially through CLI arguments accepted by the Electron main process and a reusable simulation launch parser/service.

Example shape:

```text
npm run studio -- --open <artifact-or-replay>
                 --world <world-id>
                 --speed 16
                 --autoplay
                 --evidence-dir <path>
                 --builder-job <job-id>
```

The exact flags remain typed and validated in Studio code; Tavall Agent Builder does not construct arbitrary shell fragments.

The UI still uses the same internal `loadArtifact` / `ReplaySceneSource` / renderer flow. Dialog IPC becomes one caller of a shared artifact-opening service rather than the only way to open content.

## Security/authority

- Agents never infer workspace or executable authority.
- Tavall Cloud/runtime host grants the Builder Studio executable and workspace scope.
- Studio paths must resolve inside the authorized workspace/artifact roots.
- No production Minecraft world mutation is authorized by Studio simulation.
- No arbitrary shell command field is accepted by the Builder agent contract.
- Distributed model execution remains independent of Studio execution; Studio is an executable simulation capability, not an MCP function and not an AI provider.

## Testing

### Tavall AI repository

- agent registry discovery after `TavallAgent*` rename;
- no `tavall-ai-agent-*` active Gradle modules;
- no transitional `tavall-ai-agent-core` active module;
- runtime depends on bootstrap;
- distributed execution module remains runtime-owned;
- Builder agent discovery and dependency on distributed execution;
- Builder Studio runner command construction/path validation/status mapping.

### Project Novus Builder Studio

- CLI parsing for artifact/world/speed/autoplay/evidence/job correlation;
- invalid path/speed/tick rejection;
- direct artifact opening without file dialogs;
- replay world selection and initial tick behavior;
- autoplay request reaches renderer/session state;
- evidence output path propagation;
- normal interactive `npm run studio` remains functional;
- existing 26.1.2 compatibility gate remains mandatory.

## Migration sequencing

1. Correct PR #8 naming and bootstrap hierarchy.
2. Add Builder Studio launch contract to Project Novus as a new child of the active repository staging branch.
3. Wire `tavall-agent-builder` to the Studio runner contract without importing Builder implementation.
4. Continue the already-approved Function Catalog -> Tavall AI runtime/provider ownership migration in a stacked PR.
5. Run Java 25 exact-head Tavall AI checks plus Builder/Studio Node/Windows/live acceptance when the appropriate Tavall execution surface is available.
