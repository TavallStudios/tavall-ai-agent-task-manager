---
name: tavall-ai-builder
description: Compose Tavall AI around the existing Minecraft Builder platform for planning, terrain, architecture, detail, repair, visual critique, replay evidence, and distributed model calls without duplicating Builder implementation.
---

# Tavall AI Builder

Use this skill for Tavall Builder jobs. Builder is a Tavall AI **domain module**, not a separate model runtime and not a production Minecraft gameplay runtime.

## Authoritative Builder implementation

When the target workspace contains Project Novus Builder sources, treat the existing `minecraft-bot-builder` contracts and `minecraft-bot-builder/skills/minecraft-builder/` skill as authoritative for Minecraft building behavior.

Do not copy palettes, block knowledge, BuildSpec logic, schematic serialization, mock-world simulation, replay logic, Prismarine/Studio rendering, FAWE placement, Mineflayer traversal, or world-foundry code into Tavall AI.

Tavall AI owns composition around those contracts.

## Builder behaviors

Compose the smallest useful set of Builder behaviors:

- Planner: convert intent/reference evidence into a bounded build plan/BuildSpec.
- Terrain: shape terrain and circulation while preserving gameplay constraints.
- Architecture: own structural massing, routes, interiors/exteriors, landmarks and readability.
- Detail: refine palettes, props and local visual language without destroying navigation/gameplay.
- Repair: consume validation/visual findings and make bounded corrective passes.
- Visual Critic: inspect rendered/replay evidence and produce explicit findings rather than silently mutating artifacts.

Generic Tavall implementation, review, reconciliation and E2E role modules may be composed around these domain behaviors where useful.

## Distributed AI calls

Use `tavall-ai-distributed-execution` for genuinely model-shaped work that can be satisfied by an authorized node or web runtime, especially ambiguous planning, visual critique, repair reasoning, or multimodal inspection.

Keep deterministic Builder compilation/validation local when it does not need another model call. Do not turn every geometry operation into a distributed AI request merely because distributed execution exists.

Every distributed call must preserve the durable job correlation, required capabilities, allowed/preferred execution surfaces, attempt evidence and final result/artifact reference.

## Artifact boundary

Treat these as first-class handoff/evidence kinds:

- BuildSpec;
- Sponge `.schem`;
- Tavall replay;
- visual evidence;
- WorldBakeManifest.

Prefer replay-first/mock validation for iteration. Live Paper + FAWE + Mineflayer remains a later certification boundary where required.

## Safety and authority

Cloud remains authoritative for workspace, process/network, executable/credential and target mutation grants. Builder does not gain production-world authority because it can produce a valid schematic.

Do not mutate production worlds during authoring or acceptance. Keep Builder/Studio dependencies out of production Minecraft runtime packaging.
