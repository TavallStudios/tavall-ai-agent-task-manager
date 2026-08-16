# Minecraft Agent Family + WorldOps Integration Implementation Plan

**Goal:** Replace the standalone Builder package with a `tavall-agent-minecraft` family that publishes the Minecraft coordinator plus specialized Builder, Observer, Traversal Validator, and Gameplay Validator agents and requests canonical typed WorldOps functions.

**Architecture:** Extend the existing one-provider-per-package model so one provider may publish multiple `TavallAgent` entries through a backward-compatible `agents()` method. Preserve Tavall DI/provider-index discovery and Tavall Registry. Project Novus remains the Minecraft execution implementation.

## Testing discipline

Follow Tavall delegate-style testing, not generic RED-first TDD.

- Implement a real production boundary, then exercise that real concrete behavior at the narrowest meaningful boundary.
- Use real domain objects, enums, interfaces, and concrete implementations wherever possible.
- Fake only true external boundaries.
- Java test classes match the production class name plus `Test` and mirror the production package.
- Tests, production code, DI bindings, imports, and direct documentation move together when they describe the same coherent contract change.
- Do not create tests whose purpose is to fail because a future class does not exist.
- Run repository-owned local verification only; never GitHub Actions.

## Task 1: Generalize provider-family publication

**Production:**
- Modify `TavallAgentProvider` to retain `agent()` compatibility and add a default `agents()` returning the single existing agent.
- Modify `TavallAgentRegistry` to register all agents returned by each provider while preserving duplicate-id rejection.

**Tests with the implementation:**
- Extend or create `TavallAgentProviderTest` using concrete test agents/providers to prove one-agent compatibility and multi-agent publication.
- Extend `TavallAgentRegistryTest` to exercise real family registration and duplicate rejection.

Commit provider contract, registry behavior, and matching tests as one coherent boundary.

## Task 2: Migrate Builder into `tavall-agent-minecraft`

- Rename the active Gradle module from `tavall-agent-builder` to `tavall-agent-minecraft`.
- Move Builder production classes/resources under the Minecraft family while preserving the stable `builder` id and existing Builder Studio/domain contracts.
- Create one indexed `MinecraftAgentProvider` for the package.
- Update runtime composition and provider-index checks.
- Move existing Builder tests with the production classes and retain their real behavior coverage.

## Task 3: Add concrete Minecraft specialists

Implement real definitions for:

- `minecraft`
- `builder`
- `minecraft-observer`
- `minecraft-traversal-validator`
- `minecraft-gameplay-validator`

`MinecraftAgentProviderTest` should instantiate the real provider and verify the real returned agent definitions, capabilities, runtime requirements, function requests, and mutation boundaries. Observer/Traversal/GamePlay specialists must not request WorldOps mutation functions by default.

Keep Builder internal roles: Planner, Terrain, Architecture, Detail, Repair, Visual Critic.

## Task 4: Bind Builder to canonical WorldOps names

Update the real Builder/Minecraft agent definitions to request the initial canonical `minecraft_world_*` mutation surface.

Exercise those requests through the real `MinecraftAgentProvider`/agent definitions. Assert the actual requested-function sets do not contain generic command, chat, shell, or `worldedit_command` authority.

## Task 5: Runtime integration

After the concrete family is present, exercise the installed runtime composition through the existing runtime integration test class or a production-class-matched test boundary. Verify both supported Tavall AI runtime compositions discover the same Minecraft family and retain Builder compatibility.

## Task 6: Exact-head validation and handoff

- Run `scripts/ci/verify` on Java 25.
- Confirm exactly one Minecraft provider index and no first-party ServiceLoader descriptor.
- Confirm existing Builder Studio/domain tests remain green.
- Record exactly what ran and remaining gaps.
- Keep Draft until Function Catalog and Project Novus cross-repository disposable Minecraft acceptance is truthful.