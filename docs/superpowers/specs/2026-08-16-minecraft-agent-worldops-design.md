# Tavall Minecraft Agent Family + WorldOps Design

> Owner-directed architecture correction captured on 2026-08-16. This extends the existing Tavall AI agent runtime, Function Catalog, and Project Novus Mineflayer/FAWE Builder stack rather than creating a parallel Minecraft automation system.

## Decision

Create `tavall-agent-minecraft` in Tavall AI as the parent package for the specialized Minecraft agent family. Migrate the current standalone Builder agent beneath that family, preserve its Builder behavior roles, and add the existing observer/validation bot specializations as independently addressable child agents.

Expose Minecraft world mutation through canonical typed Function Catalog functions. Those functions express Tavall-owned world-operation semantics; they are not raw WorldEdit command passthroughs.

Project Novus remains authoritative for the actual Minecraft execution implementation. Mineflayer bots are the in-game execution actors and FAWE/WorldEdit is the bulk world-edit engine. The existing `FaweCommandClient`, `TavallBuilder`, observation stack, traversal validation, schematics, replay, Studio, and world-foundry machinery are reused rather than duplicated.

## Existing architecture that must be preserved

### Tavall AI

The current platform baseline is Tavall AI PR #16, stacked on the runtime/agent architecture in PR #8. It uses Tavall DI/provider indexes and Tavall Registry-backed agent discovery. First-party ServiceLoader composition must not be reintroduced.

PR #8 currently models Builder as a standalone `tavall-agent-builder` package. The approved correction is to make Builder a specialization under `tavall-agent-minecraft`.

### Project Novus

`minecraft-bot-builder` already owns the relevant implementation seams:

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

VibeCraft source commit `be5045f20027b60dd1d1a8604379c51ebf84e2f4` is a seed/reference corpus, not a runtime dependency. Its WorldEdit wrappers and JSON knowledge can be used to recover coverage and data. Any copied or substantially derived source/data must preserve the upstream MIT copyright/license notice.

VibeCraft data belongs with Project Novus Builder knowledge/assets, not in Function Catalog. Function Catalog stores semantics and schemas, not building-template/furniture knowledge.

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

This preserves the current one-provider-index-per-package invariant while allowing `tavall-agent-minecraft` to contain the bot-agent family the product already uses.

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

The `builder` id remains stable during the package migration so existing Tavall AI references do not break merely because ownership moves under the Minecraft family.

The `minecraft` coordinator declares subagent orchestration capability and routes work to specialists. Specialist function views stay narrow:

- Builder may request authorized world-mutation functions plus existing Builder simulation/evidence capabilities.
- Observer is read/evidence oriented and receives no WorldEdit mutation authority by default.
- Traversal validator is navigation/acceptance oriented and receives no WorldEdit mutation authority by default.
- Gameplay validator is player-flow/multi-bot acceptance oriented and receives no WorldEdit mutation authority by default.

Agent metadata requests functions. It never grants server, bot, world, credential, or production authority.

## Canonical WorldOps function surface

The first implementation slice deliberately covers the FAWE capabilities Project Novus already implements today. This gives the agent family useful typed world editing without inventing a second command language.

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

There is intentionally **no** `minecraft_world_command`, `worldedit_command`, generic chat command, shell field, or arbitrary `//...` argument.

### Typed data

Public requests use typed values such as:

- integer block positions;
- normalized min/max block regions;
- Minecraft block ids/states;
- typed weighted block palettes when mixed patterns are introduced;
- typed clipboard rotations and flip directions;
- validated schematic identifiers and formats;
- logical world references that are checked against the provider's host-authorized scope.

Raw WorldEdit pattern/command strings are an executor implementation detail. Credentials, server addresses, operator permissions, and production-world authority never appear as model-selectable function arguments.

### Provider boundary

`MinecraftWorldOpsFunctions` delegates semantic validation and normalization to `MinecraftWorldOpsService`. The service delegates external Minecraft I/O to `MinecraftWorldOpsProvider`.

The provider is execution-scoped by the runtime/host. A model cannot gain a world merely by naming it. The provider must reject targets outside the host-authorized execution scope.

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

`TavallBuilder` should reuse the same WorldOps/FAWE client path rather than maintaining a separate set of command translations.

### Operation-level serialization

The existing `FaweCommandClient` serializes individual chat commands, but a WorldEdit operation can require multiple commands whose selection state must remain atomic:

```text
//pos1 ...
//pos2 ...
//replace ...
```

Concurrent operations on the same bot must not interleave those sequences. `MineflayerWorldOpsExecutor` therefore owns an operation-level queue/mutex above `FaweCommandClient`.

This is required even though individual chat sends are already queued.

### Bot authority

Builder/world-editor bots may receive WorldEdit permissions only in host-authorized development/disposable worlds for normal automated work. Observer, traversal, and gameplay-validation bots remain non-mutating by default.

Production world mutation is a separate explicit authorization boundary and is not enabled by this design.

## VibeCraft expansion path

After the existing FAWE surface is typed and live-certified, use VibeCraft as a coverage checklist to expand Tavall WorldOps rather than exposing its generic command buckets verbatim.

Candidate Tavall-owned additions include:

- region faces, overlay, move, and stack;
- typed sphere, hollow sphere, cylinder, hollow cylinder, pyramid, and hollow pyramid generation;
- terrain smoothing/naturalization and terrain-pattern operations;
- spatial/world scanning and clearance analysis;
- block/item lookup;
- reusable building-pattern, terrain-pattern, furniture, and template knowledge imported into Project Novus Builder data with required attribution;
- preview/diff/rollback orchestration built on Project Novus simulation/replay plus FAWE history where the semantics are proven.

Each addition becomes a typed function or Project Novus knowledge primitive. No generic command escape hatch is added as a shortcut.

## Testing and acceptance

### Tavall AI

- RED first: runtime agent registry expects the Minecraft coordinator and all four specialists while the current branch still exposes standalone Builder only.
- Provider-family compatibility tests prove existing one-agent providers still work unchanged.
- Registry rejects duplicate ids across family providers.
- Builder Studio/domain contracts survive the package migration.
- Java 25 exact-head local verification through the repository-owned verifier.

### Function Catalog

- RED first: MCP/catalog contract expects the fourteen initial `minecraft_world_*` functions and no generic command function.
- Typed request validation rejects malformed regions, unsafe schematic ids, invalid rotations/flips, and unscoped targets.
- Provider tests prove normalized typed requests reach the provider and provider failures remain explicit.
- Automatic MCP projection exposes the canonical schemas without a second hand-written MCP implementation.
- Java 25 exact-head local verification through the repository-owned verifier.

### Project Novus

- RED first: concurrent region operations demonstrate the current selection-command interleaving hazard.
- WorldOps executor serializes whole selection+operation sequences.
- Every initial typed operation maps deterministically to the existing safe FAWE client.
- Existing command-injection protections remain intact.
- `bot.tavallWorldOps` and `bot.tavallBuilder` share the canonical execution path.
- npm typecheck/test/build gates pass.
- Disposable Paper + FAWE + Mineflayer acceptance proves real commands, undo/redo, schematic operations, and a concurrent multi-bot scenario.

No GitHub-hosted workflow is used for these gates. No production world mutation is part of acceptance.

## PR ownership / stacking

- **Tavall AI:** `working/minecraft-agent-family-worldops`, stacked on Java Tools platform PR #16.
- **Function Catalog:** `working/minecraft-worldops-functions`, stacked on Java Tools platform PR #15.
- **Project Novus:** `working/minecraft-worldops-mineflayer`, based on current Combined Runtime Staging #153 so it sees the merged Builder implementation.

All three remain Draft until their exact-head local checks and cross-repository disposable Minecraft acceptance are truthful.