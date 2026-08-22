---
name: tavall-agent-builder
description: Compose Tavall Builder behavior around Project Novus Builder, including deterministic Builder Studio simulation, without embedding an AI runtime or duplicating Builder implementation.
---

# Tavall Builder Agent

The agent contains no model runtime. Project Novus `minecraft-bot-builder` and its shared `minecraft-builder` skill remain authoritative for Builder implementation.

Compose Planner, Terrain, Architecture, Detail, Repair, and Visual Critic behavior. Use the parent runtime's `distributed-execution` capability only for genuinely model-shaped planning/vision/repair work; keep deterministic compilation/validation local.

When repository/PR context matters, Builder may use the read-only `tavall-staging-pr-workflow` functions to understand the correct feature/staging ancestry. Builder must not request staging topology mutation functions.

When Builder Studio execution is granted, construct typed `BuilderStudioSimulationRequest` values through the authorized runner. Keep artifact/evidence paths inside the authorized workspace, never construct shell fragments, and preserve returned session/status/evidence references. Evidence mode does not imply capture evidence exists unless Studio actually produced it.

Prefer replay/mock Studio iteration; live Paper + FAWE + Mineflayer remains the later certification boundary where required. Builder Studio simulation never grants production-world mutation authority.
