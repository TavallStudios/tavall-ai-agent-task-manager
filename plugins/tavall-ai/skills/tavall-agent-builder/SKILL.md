---
name: tavall-agent-builder
description: Coordinate Tavall Builder work around the authoritative Project Novus Minecraft Builder platform, including MineBench-derived first-concept generation, deterministic Builder Studio verification, semantic repair, and explicit production certification.
---

# Tavall Builder Agent

Use this coordinator for Tavall Builder jobs. It does not own Minecraft geometry, palettes, schematic formats, simulation, rendering, or a second AI runtime. The parent Tavall AI runtime supplies model execution; `minecraft-bot-builder` owns Builder behavior.

## Authoritative Builder implementation

When Project Novus Builder sources are present, treat `minecraft-bot-builder` and `minecraft-bot-builder/skills/minecraft-builder/` as authoritative. Do not duplicate BuildSpec logic, MineBench voxel runtime, palettes, schematic serialization, mock/world-vision logic, Builder Studio rendering, FAWE placement, Mineflayer traversal, verification agents, or repair-learning logic in Tavall AI.

## Default orchestration

For a new build or substantial redesign, coordinate this sequence unless the authoritative Builder skill declares an exception:

`intent/references/constraints -> retrieve Builder context + palette -> distributed model call using Builder's MineBench concept prompt -> preserve exact voxel.exec tool call -> create inert `minebench-concept` BuildSpec -> deterministic compile/mock/world-vision validation -> Builder Studio render/evidence -> distributed multimodal critique using the combined MineBench + Tavall rubric -> bounded semantic repair -> re-render/re-verify -> live certification where required`

Do not replace the MineBench-derived concept pass with an improvised Planner-only first draft. Planner, Terrain, Architecture, and Detail roles may enrich constraints and later repairs, but the authoritative first-generation contract comes from `minecraft-builder`. Existing authored maps, exact geometry preservation, imports, and explicit repair-only jobs follow the Builder skill's exceptions.

## Model calls

Use the parent runtime's `distributed-execution` capability for genuinely model-shaped operations:

- first-concept synthesis using `buildMineBenchConceptPrompts(...)`;
- multimodal visual critique using `buildBuilderVisualCritiquePrompt(...)`;
- semantic repair planning using `buildBuilderRepairPrompt(...)`.

Keep deterministic voxel execution, BuildSpec lowering, compilation, validation, world vision, replay, artifact generation, and verification local to the Builder implementation. A model call must not gain arbitrary shell, Node, browser, FAWE, or production-world authority merely because MineBench's bounded `voxel.exec` vocabulary uses JavaScript syntax.

Preserve the first concept's exact program, seed, palette, prompt constraints, source provenance, artifact identity, and evidence references across repair calls. Repairs edit the accepted source; they do not silently generate an unrelated replacement.

## Combined review contract

The visual reviewer must retain the MineBench criteria after generation: recognizability, true 3D structure, prompt fidelity, proportions/scale, detail quality, scene composition, and overall impression. It must also enforce Tavall gameplay readability, traversal, encounter fairness, palette cohesion, world context, performance/density, and production validity.

A mechanically valid but visually mediocre build is not done. A visually strong concept that fails blocking Tavall gameplay or production criteria is also not done.

## Builder Studio simulation

When the runtime grants Builder Studio execution, use typed `BuilderStudioSimulationRequest` values and the authorized `BuilderStudioSimulationRunner` boundary. Artifact/evidence paths must remain inside the authorized Builder environment component; allowed playback speeds are `0.25`, `1`, `4`, `16`, `64`.

Use deterministic mock/world-vision/replay simulation for iteration and preserve returned session/status/evidence references. Visual review must use actual Builder-produced renders/evidence. Do not substitute generated images, imagined screenshots, or prose-only visual claims.

Live Paper + FAWE + Mineflayer remains a later certification boundary where required. Studio or replay evidence is not proof that a live runtime gate ran.

## Lane and environment authority

Tavall lanes and immutable developer environment generations are the top-level orchestration identity for Builder engineering work. Resolve exact multi-repository source snapshots before implementation/validation, carry the lane/environment/source-snapshot fence into staging operations, and resolve a new environment generation after each committed source-head change that matters to acceptance.

Low-level checkout, sandbox, and job APIs are implementation components beneath that environment. Do not use the deprecated standalone workspace catalog as the top-level workflow or infer environment acceptance from an unfenced checkout.

## Authority

Tavall Cloud/runtime host remains authoritative for source snapshots, workspace/process/network grants, executable/credential authority, environment components, and target mutation. Builder Studio simulation never grants production-world mutation authority.
