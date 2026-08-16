# Minecraft Agent Family + WorldOps Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the standalone Builder package with a `tavall-agent-minecraft` family that publishes the Minecraft coordinator plus specialized Builder/Observer/Traversal/GamePlay validation agents and requests canonical typed WorldOps functions.

**Architecture:** Extend the existing one-provider-per-package model so a provider may publish multiple `TavallAgent` entries through a backward-compatible default `agents()` method. Keep Tavall DI/provider-index discovery and Tavall Registry. Migrate existing Builder contracts under the Minecraft package without moving Project Novus implementation into Tavall AI.

**Tech Stack:** Java 25, Gradle, Tavall DI, Tavall Registry, JUnit 5, Function Catalog function-name contracts.

## Global Constraints

- Base is `working/tavall-java-tools-platform-adoption` / Tavall AI PR #16.
- Do not restore first-party ServiceLoader discovery.
- Preserve existing `builder` agent id during package migration.
- Tavall AI contains behavior/contracts/function requests only; Project Novus owns Mineflayer/FAWE/Builder implementation.
- Agent metadata never grants Minecraft server/world/process/credential authority.
- No production code before the corresponding RED test is executed and fails for the expected reason.
- Run repository-owned local verification only; do not use GitHub Actions.

---

## Task 1: Prove the missing Minecraft family at runtime

**Files:**
- Create: `tavall-ai-runtime/src/test/java/org/tavall/ai/runtime/MinecraftAgentFamilyRuntimeTest.java`

- [ ] Add a runtime test that loads `TavallAgentRegistry` from the runtime classpath and expects these ids: `minecraft`, `builder`, `minecraft-observer`, `minecraft-traversal-validator`, `minecraft-gameplay-validator`.
- [ ] Assert `builder` remains present so the migration cannot silently break existing callers.
- [ ] Run the focused runtime test and record the expected RED result showing the family ids are absent on the current baseline.
- [ ] Commit the RED test independently.

## Task 2: Generalize one provider to publish an agent family

**Files:**
- Modify: `tavall-ai-bootstrap/src/main/java/org/tavall/agent/TavallAgentProvider.java`
- Modify: `tavall-ai-bootstrap/src/main/java/org/tavall/agent/TavallAgentRegistry.java`
- Modify: `tavall-ai-bootstrap/src/test/java/org/tavall/agent/TavallAgentRegistryTest.java` or the closest existing registry test

- [ ] Add a RED unit test proving a provider can publish two agents while an unchanged single-agent provider still publishes exactly one.
- [ ] Run the focused bootstrap test and verify RED because `TavallAgentProvider` has no family contract yet.
- [ ] Add `default Collection<TavallAgent> agents() { return List.of(agent()); }`.
- [ ] Update `TavallAgentRegistry` to register every entry from `provider.agents()` and preserve duplicate-id rejection across providers/families.
- [ ] Run focused tests to GREEN.
- [ ] Refactor comments away from obsolete ServiceLoader wording while preserving behavior.
- [ ] Commit the provider-family contract.

## Task 3: Migrate Builder into `tavall-agent-minecraft`

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Move: `tavall-agent-builder/src/main/**` -> `tavall-agent-minecraft/src/main/**`
- Move: `tavall-agent-builder/src/test/**` -> `tavall-agent-minecraft/src/test/**`
- Create: `tavall-agent-minecraft/src/main/java/org/tavall/agent/minecraft/MinecraftAgentProvider.java`
- Create: `tavall-agent-minecraft/src/main/resources/META-INF/tavall/agent-provider`
- Create: `tavall-agent-minecraft/src/main/resources/org/tavall/agent/minecraft/ROLE.md`

- [ ] Add/adjust RED source/runtime assertions that `tavall-agent-minecraft` is the active module and `tavall-agent-builder` is no longer active.
- [ ] Run the focused test and verify RED on the old module layout.
- [ ] Rename the Gradle project to `tavall-agent-minecraft` and update the agent project list/runtime dependency.
- [ ] Move Builder implementation under `org.tavall.agent.minecraft.builder` while preserving Builder Studio request/runner contracts.
- [ ] Create one indexed `MinecraftAgentProvider`; do not create multiple provider index entries.
- [ ] Keep `builder` as the Builder specialist's stable agent id.
- [ ] Run migrated Builder tests and runtime family test.
- [ ] Commit the package migration.

## Task 4: Add the specialized Minecraft agents

**Files:**
- Create: `tavall-agent-minecraft/src/main/java/org/tavall/agent/minecraft/MinecraftAgentIds.java`
- Create: `tavall-agent-minecraft/src/main/java/org/tavall/agent/minecraft/MinecraftAgentContract.java`
- Create: `tavall-agent-minecraft/src/main/resources/org/tavall/agent/minecraft/roles/OBSERVER.md`
- Create: `tavall-agent-minecraft/src/main/resources/org/tavall/agent/minecraft/roles/TRAVERSAL_VALIDATOR.md`
- Create: `tavall-agent-minecraft/src/main/resources/org/tavall/agent/minecraft/roles/GAMEPLAY_VALIDATOR.md`
- Modify: `tavall-agent-minecraft/src/test/**`

- [ ] Add RED tests for the coordinator and specialist ids, descriptions, function requests, runtime requirements, and mutation boundaries.
- [ ] Prove Observer, Traversal Validator, and Gameplay Validator do not request WorldOps mutation functions.
- [ ] Prove the Minecraft coordinator may spawn subagents and declares `SUBAGENT_ORCHESTRATION`.
- [ ] Implement the minimal specialist definitions and instructions to satisfy those contracts.
- [ ] Keep existing Builder internal behavior roles: Planner, Terrain, Architecture, Detail, Repair, Visual Critic.
- [ ] Run focused tests to GREEN and commit.

## Task 5: Bind Builder to canonical WorldOps names

**Files:**
- Modify: `tavall-agent-minecraft/src/main/java/org/tavall/agent/minecraft/MinecraftAgentContract.java`
- Modify: `tavall-agent-minecraft/src/test/**`

- [ ] Add RED assertions that Builder requests exactly the initial canonical mutation surface: block set; region set/walls/replace/clear; clipboard copy/cut/paste/rotate/flip; schematic load/save; history undo/redo.
- [ ] Assert no agent requests `minecraft_world_command`, `worldedit_command`, generic chat, or shell functions.
- [ ] Implement the function-name set using constants local to Tavall AI only if Function Catalog Java types are intentionally not imported; canonical names must match Function Catalog exactly.
- [ ] Run tests to GREEN and commit.

## Task 6: Exact-head validation and cross-repository acceptance handoff

- [ ] Run `scripts/ci/verify` on Java 25 against the exact head after Function Catalog WorldOps contracts are available.
- [ ] Confirm provider-index verification sees exactly one Minecraft provider index and no first-party ServiceLoader descriptor.
- [ ] Confirm runtime registry exposes all five agent ids and Builder Studio tests remain green.
- [ ] Reconcile the Draft PR body with exact head, test evidence, Function Catalog dependency PR, and Project Novus executor dependency PR.
- [ ] Keep Draft until Project Novus disposable Paper + FAWE + Mineflayer acceptance proves the functions against a real bot executor.