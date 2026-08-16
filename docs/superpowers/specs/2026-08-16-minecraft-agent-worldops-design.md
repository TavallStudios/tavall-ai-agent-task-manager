# Tavall Minecraft Agent Family + WorldOps Design

> Owner-directed architecture correction captured on 2026-08-16. This extends the existing Tavall AI agent runtime, Function Catalog, and Project Novus Mineflayer/FAWE Builder stack rather than creating a parallel Minecraft automation system.

## Decision

Create `tavall-agent-minecraft` in Tavall AI as the parent package for the specialized Minecraft agent family. Migrate the current standalone Builder agent beneath that family, preserve its Builder behavior roles, and expose the existing observer/validation bot specializations as independently addressable child agents.

Expose Minecraft world mutation through canonical typed Function Catalog functions. Those functions express Tavall-owned world-operation semantics; they are not raw WorldEdit command passthroughs.

Project Novus remains authoritative for the actual Minecraft execution implementation. Mineflayer bots are the in-game execution actors and FAWE/WorldEdit is the bulk world-edit engine. Reuse the existing `FaweCommandClient`, `TavallBuilder`, observation stack, traversal validation, schematics, replay, Studio, and world-foundry machinery.

## Existing architecture that must be preserved

### Tavall AI

The current platform baseline is Tavall AI PR #16, stacked on the runtime/agent architecture in PR #8. It uses Tavall DI/provider indexes and Tavall Registry-backed agent discovery. First-party ServiceLoader composition must not be reintroduced.

PR #8 currently models Builder as a standalone `tavall-agent-builder` package. The approved correction is to make Builder a specialization under `tavall-agent-minecraft`.

### Project Novus

`minecraft-bot-builder` already owns:

- `FaweCommandClient` and `MineflayerChatTransport` for chat-backed FAWE/WorldEdit execution;
- `TavallBuilder` and `bot.tavallBuilder` for Mineflayer-backed build orchestration;
- deterministic BuildSpec compilation and mock-world validation;
- Sponge v3 schematics and world-bake artifacts;
- Mineflayer spectator observation and Prismarine Viewer evidence;
- Mineflayer traversal validation;
- disposable Paper + FAWE + Mineflayer as the live certification boundary.

The generic Builder implementation stays in Project Novus. Tavall AI contains behavior, composition metadata, tool requirements, and domain contracts, not a duplicate Minecraft implementation.

### Function Catalog

Function Catalog remains authoritative for canonical typed callable functions, schemas, narrowed function views, invocation policy/audit, and automatic MCP projection. Minecraft functions follow the same `Functions -> Service -> Provider` ownership shape used by other catalog domains.

### VibeCraft

VibeCraft source commit `be5045f20027b60dd1d1a8604379c51ebf84e2f4` is a seed/reference corpus, not a runtime dependency. Its WorldEdit wrappers and JSON knowledge may be used to recover coverage and data. Any copied or substantially derived source/data must preserve the upstream MIT copyright/license notice.

VibeCraft data belongs with Project Novus Builder knowledge/assets, not in Function Catalog.

## Tavall AI agent-family model

### Provider model

Keep one indexed provider class per `tavall-agent-*` Gradle package, but permit one provider to publish an agent family.

Evolve `TavallAgentProvider` compatibly:

```java
public interface TavallAgentProvider {
    TavallAgent agent();

    default Collection<TavallAgent> agents() {
        return List.of(agent());
    }
}
```

`TavallAgentRegistry` registers every entry returned by `provider.agents()`. Existing providers remain single-agent providers without source changes. `MinecraftAgentProvider` returns the Minecraft coordinator plus its specialists.

### Initial Minecraft family

```text
minecraft                         coordinator / specialist dispatcher
├── builder                       existing Builder agent id preserved
│   ├── Planner
│   ├── Terrain
│   ├── Architecture
│   ├── Detail
│   ├── Repair
│   └── Visual Critic
├── minecraft-observer            visual/world observation specialist
├── minecraft-traversal-validator navigation/traversal acceptance specialist
└── minecraft-gameplay-validator  multi-bot player-flow/gameplay acceptance specialist
```

The `builder` id remains stable during the package migration.

The `minecraft` coordinator declares subagent orchestration capability and routes work to specialists. Specialist function views stay narrow:

- Builder may request authorized world-mutation functions plus existing Builder simulation/evidence capabilities.
- Observer is read/evidence oriented and receives no WorldEdit mutation authority by default.
- Traversal Validator is navigation/acceptance oriented and receives no WorldEdit mutation authority by default.
- Gameplay Validator is player-flow/multi-bot acceptance oriented and receives no WorldEdit mutation authority by default.

Agent metadata requests functions. It never grants server, bot, world, credential, or production authority.

## Canonical WorldOps function surface

The first implementation slice covers the FAWE capabilities Project Novus already implements today.

### Block and region

- `minecraft_world_block_set`
- `minecraft_world_region_set`
- `minecraft_world_region_walls`
- `minecraft_world_region_replace`
- `minecraft_world_region_clear`

### Clipboard

- `minecraft_world_clipboard_copy`
- `minecraft_world_clipboard_cut`
- `minecraft_world_clipboard_paste`
- `minecraft_world_clipboard_rotate`
- `minecraft_world_clipboard_flip`

### Schematics

- `minecraft_world_schematic_load`
- `minecraft_world_schematic_save`

### History

- `minecraft_world_history_undo`
- `minecraft_world_history_redo`

There is intentionally no `minecraft_world_command`, `worldedit_command`, generic chat command, shell field, or arbitrary `//...` argument.

Public requests use typed values for positions, regions, block ids/states, clipboard operations, schematic identifiers/formats, and logical world references. Raw WorldEdit command strings are an executor implementation detail.

`MinecraftWorldOpsFunctions` delegates semantic validation and normalization to `MinecraftWorldOpsService`. The service delegates external Minecraft I/O to `MinecraftWorldOpsProvider`. The provider is execution-scoped by the runtime/host and must reject targets outside the host-authorized execution scope.

Function Catalog automatically projects the annotated canonical Java functions to MCP. Tavall AI requests the canonical function names and does not duplicate their schemas.

## Project Novus Mineflayer executor

Add a first-class WorldOps layer next to the existing Builder plugin:

```text
bot.tavallWorldOps
    -> TavallWorldOps / MineflayerWorldOpsExecutor
        -> FaweCommandClient
            -> MineflayerChatTransport
                -> Mineflayer bot
                    -> FAWE / WorldEdit on Paper
```

`TavallBuilder` reuses the same WorldOps/FAWE client path rather than maintaining a separate set of command translations.

### Operation-level serialization

The existing `FaweCommandClient` serializes individual chat commands, but a WorldEdit operation may require multiple commands whose selection state must remain atomic:

```text
//pos1 ...
//pos2 ...
//replace ...
```

Concurrent operations on the same bot must not interleave those sequences. `MineflayerWorldOpsExecutor` therefore owns an operation-level queue/mutex above `FaweCommandClient`.

### Bot authority

Builder/world-editor bots may receive WorldEdit permissions only in host-authorized development/disposable worlds for normal automated work. Observer, traversal, and gameplay-validation bots remain non-mutating by default.

Production world mutation is a separate explicit authorization boundary and is not enabled by this design.

## VibeCraft expansion path

After the existing FAWE surface is typed and live-certified, use VibeCraft as a coverage checklist to expand Tavall WorldOps rather than exposing its generic command buckets verbatim.

Candidate additions include region faces/overlay/move/stack, typed shape generation, terrain operations, spatial scanning, block/item lookup, imported pattern/template/furniture knowledge, and preview/diff/rollback orchestration where semantics are proven.

## Testing and acceptance

Tavall testing policy governs this work. Generic RED-first TDD does not override repository quality rules.

Tests must exercise real production behavior at the narrowest meaningful boundary. Use real domain objects, enums, interfaces, and concrete implementations wherever possible. Fake only true external boundaries such as host providers, Mineflayer transport edges, test clocks, or disposable infrastructure. Do not create tests whose purpose is merely to prove that a future class or function is missing.

Tests and production behavior move together as one coherent system boundary. Java test classes match the production class name plus `Test`, and package structure mirrors production structure. TypeScript/Mineflayer tests should likewise target the concrete executor/plugin behavior rather than dynamic-import absence checks.

### Tavall AI

- `TavallAgentProviderTest` exercises the real provider-family compatibility behavior using concrete Tavall agents.
- `TavallAgentRegistryTest` exercises real registration, duplicate-id rejection, and family/single-provider compatibility.
- `MinecraftAgentProviderTest` exercises the concrete coordinator/specialist definitions, function requests, and mutation boundaries.
- Existing Builder tests move with the Builder implementation and continue exercising the real Builder Studio/domain contracts.
- Runtime integration tests verify the installed agent universe only after the concrete provider/package is present.
- Java 25 exact-head local verification runs through the repository-owned verifier.

### Function Catalog

- `MinecraftBlockRegionTest`, `MinecraftWorldOpsServiceTest`, `MinecraftWorldOpsFunctionsTest`, and `MinecraftWorldOpsRegistrarTest` exercise the actual concrete value/service/function/registration boundaries.
- Provider tests use a narrow fake external provider while calling the real service/functions.
- MCP projection tests register the real `MinecraftWorldOpsFunctions` and inspect the actual projected schemas.
- Validation covers malformed regions, unsafe schematic ids, invalid rotations/flips, unscoped targets, provider failures, and absence of generic command authority.
- Java 25 exact-head local verification runs through the repository-owned verifier.

### Project Novus

- `MineflayerWorldOpsExecutor` tests use the real executor and real `FaweCommandClient`, faking only the chat transport where a live Minecraft server is not the boundary under test.
- Concurrency coverage invokes real executor operations and verifies complete selection+mutation sequencing.
- `TavallWorldOps` plugin tests exercise the real Mineflayer plugin composition.
- `TavallBuilder` tests verify Builder and direct WorldOps share the canonical execution path.
- Mineflayer raw TypeScript acceptance exercises real commands, undo/redo, schematics, traversal, observation, and multi-bot behavior in disposable Paper + FAWE.
- npm typecheck/test/build gates run locally.

Validation reports state exactly what ran and what remains untested. No GitHub-hosted workflow is used for these gates, and no production world mutation is part of acceptance.

## PR ownership / stacking

- **Tavall AI:** `working/minecraft-agent-family-worldops`, stacked on Java Tools platform PR #16.
- **Function Catalog:** `working/minecraft-worldops-functions`, stacked on Java Tools platform PR #15.
- **Project Novus:** `working/minecraft-worldops-mineflayer`, based on current Combined Runtime Staging #153.

All three remain Draft until their exact-head local checks and cross-repository disposable Minecraft acceptance are truthful.