# Tavall Agent Runtime + Builder Studio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct Tavall agent/runtime naming and composition, remove AI identity from agent packages, make runtime modules parent bootstrap consumers, and let the Builder agent launch deterministic simulations in Tavall Builder Studio.

**Architecture:** `tavall-ai-runtime` is the executable parent layer. `tavall-ai-bootstrap` owns reusable agent/module discovery. `tavall-agent-*` packages contain no model runtime and are loaded by runtimes. Distributed AI routing is a runtime module. Builder Studio remains in Project Novus and exposes a narrow typed launch contract consumed by `tavall-agent-builder` through an authorized executable runner.

**Tech Stack:** Java 25, Gradle Kotlin DSL, Java ServiceLoader, JUnit 5; Node 22+, TypeScript 5.7, Electron 43, Prismarine Viewer, existing Tavall Builder replay/scene APIs.

## Global Constraints

- Agent artifacts use `tavall-agent-*`, never `tavall-ai-agent-*` or `tavall-ai-module-*`.
- Agents contain no embedded model/AI runtime.
- Runtime modules are the parent execution layer and depend on `tavall-ai-bootstrap`.
- Remove active `tavall-ai-agent-core`; move its contracts into `tavall-ai-bootstrap`.
- Distributed AI routing remains runtime-owned as `tavall-ai-runtime-distributed-execution`.
- Builder is `tavall-agent-builder`.
- Builder Studio owns Electron/Prismarine/replay rendering and simulation execution.
- Tavall Agent Builder receives only a narrow authorized Studio executable contract; no arbitrary shell field.
- Builder Studio simulation never authorizes production-world mutation.
- Preserve Java 25 and Node 22+ verification gates.

---

### Task 1: Move agent contracts into bootstrap and remove AI identity

**Files:**
- Move active Java contracts from `tavall-ai-agent-core/src/main/java/org/tavall/ai/agent/role/*`
- Create equivalent contracts under `tavall-ai-bootstrap/src/main/java/org/tavall/agent/*`
- Move/replace tests from `tavall-ai-agent-core/src/test/...`
- Modify `settings.gradle.kts`
- Modify `build.gradle.kts`

**Interfaces:**
- Produces: `TavallAgent`, `TavallAgentProvider`, `TavallAgentRegistry`, `TavallAgentKind`, `TavallAgentCapability`, `TavallAgentInstructions`.
- `TavallAgentRegistry.load(ClassLoader)` remains ServiceLoader-backed.

- [ ] **Step 1: Write failing bootstrap agent-registry tests** proving duplicate IDs fail, providers discover, requested function names remain immutable, and AI/model/runtime semantics are absent from the public agent contract.
- [ ] **Step 2: Run `./gradlew :tavall-ai-bootstrap:test` and confirm failure** because the `org.tavall.agent` contracts do not exist yet.
- [ ] **Step 3: Move the minimal role-contract behavior into `tavall-ai-bootstrap`** with package names under `org.tavall.agent` and rename public types from `TavallAIAgent*` to `TavallAgent*`.
- [ ] **Step 4: Update ServiceLoader provider service name** to `org.tavall.agent.TavallAgentProvider`.
- [ ] **Step 5: Remove `tavall-ai-agent-core` from active Gradle settings/dependencies.**
- [ ] **Step 6: Run bootstrap tests and descriptor verification.**
- [ ] **Step 7: Commit:** `refactor: move Tavall agent contracts into bootstrap`.

### Task 2: Rename all role packages to `tavall-agent-*`

**Files:**
- Rename Gradle projects/directories:
  - `tavall-ai-agent-scheduler` -> `tavall-agent-scheduler`
  - `tavall-ai-agent-orchestration` -> `tavall-agent-orchestration`
  - `tavall-ai-agent-implementation` -> `tavall-agent-implementation`
  - `tavall-ai-agent-review` -> `tavall-agent-review`
  - `tavall-ai-agent-reconciliation` -> `tavall-agent-reconciliation`
  - `tavall-ai-agent-e2e` -> `tavall-agent-e2e`
  - `tavall-ai-agent-architecture` -> `tavall-agent-architecture`
  - `tavall-ai-agent-documentation` -> `tavall-agent-documentation`
- Rename plugin skill folders from `tavall-ai-agent-*` to `tavall-agent-*`.
- Modify all provider imports and ServiceLoader files.

**Interfaces:**
- Each agent publishes exactly one `TavallAgentProvider`.
- Existing agent IDs remain stable (`scheduler`, `orchestration`, etc.).

- [ ] **Step 1: Add/modify build verification** to fail if any active project name starts with `tavall-ai-agent-`.
- [ ] **Step 2: Run the verification and confirm it fails.**
- [ ] **Step 3: Rename projects, Java imports/types, ServiceLoader registrations, role docs and skill directories.**
- [ ] **Step 4: Change architecture wording from “role module” where it describes the package identity to “agent”; retain “module” only for actual bootstrap/runtime module mechanics.**
- [ ] **Step 5: Run `./gradlew clean check` on Java 25.**
- [ ] **Step 6: Commit:** `refactor: remove AI identity from Tavall agents`.

### Task 3: Make distributed execution explicitly runtime-owned

**Files:**
- Rename `tavall-ai-module-distributed-execution` -> `tavall-ai-runtime-distributed-execution`.
- Modify package/build references in `settings.gradle.kts`, `tavall-ai-runtime/build.gradle.kts`, tests and docs.

**Interfaces:**
- Preserve existing `TavallAIExecution*` runtime contracts because these represent actual AI/model execution.
- Preserve `DistributedExecutionModuleProvider` semantics but publish it as a runtime capability module discovered by bootstrap.

- [ ] **Step 1: Add a build test/verification that requires distributed execution to use the `tavall-ai-runtime-*` prefix.**
- [ ] **Step 2: Run and confirm failure under the current name.**
- [ ] **Step 3: Rename the Gradle project and update runtime dependency wiring.**
- [ ] **Step 4: Run distributed-execution routing tests plus runtime describe tests.**
- [ ] **Step 5: Commit:** `refactor: make distributed execution runtime owned`.

### Task 4: Rename Builder to `tavall-agent-builder` and add Studio runner contract

**Files:**
- Rename `tavall-ai-module-builder` -> `tavall-agent-builder`.
- Add under the Builder agent:
  - `BuilderStudioSimulationRequest.java`
  - `BuilderStudioSimulationResult.java`
  - `BuilderStudioSimulationStatus.java`
  - `BuilderStudioSimulationRunner.java`
  - `BuilderStudioCommandFactory.java`
- Modify Builder skill and tests.

**Interfaces:**

```java
public interface BuilderStudioSimulationRunner {
    BuilderStudioSimulationResult run(BuilderStudioSimulationRequest request) throws Exception;
}
```

`BuilderStudioSimulationRequest` contains:
- `String jobId`
- `Path workspaceRoot`
- `Path artifactPath`
- `Optional<String> worldId`
- `double playbackSpeed`
- `boolean autoplay`
- `OptionalLong initialTick`
- `OptionalLong finalTick`
- `OptionalInt visibleEntityCap`
- `Optional<Path> evidenceDirectory`
- `BuilderStudioSimulationMode mode` (`VISIBLE`, `EVIDENCE`)

`BuilderStudioCommandFactory` returns `List<String>` arguments only after path containment and value validation. It must never accept an arbitrary shell fragment.

- [ ] **Step 1: Write failing tests** for workspace containment, allowed playback speeds, tick ordering, evidence-path containment, and exact CLI argument construction.
- [ ] **Step 2: Run Builder agent tests and confirm failure.**
- [ ] **Step 3: Implement typed request/result/status/mode contracts and command factory.**
- [ ] **Step 4: Add a process-backed runner interface/adapter boundary that consumes an explicitly configured Studio executable/launcher; do not embed Electron or npm implementation.**
- [ ] **Step 5: Update Builder skill to use Studio simulation for visual/replay iteration and preserve live Paper/FAWE/Mineflayer as later certification.**
- [ ] **Step 6: Run Builder agent tests.**
- [ ] **Step 7: Commit:** `feat: let Builder agent drive Studio simulations`.

### Task 5: Add deterministic Builder Studio launch contract in Project Novus

**Repository:** `TavallStudios/tavall-project-novus`

**Branching:** Create a new child branch from active repository staging `stabilize/full-runtime-build` and open a Draft child PR.

**Files:**
- Modify `minecraft-bot-builder/studio-app/src/main.mts`
- Create `minecraft-bot-builder/studio-app/src/simulation/StudioLaunchRequest.ts`
- Create `minecraft-bot-builder/studio-app/src/simulation/StudioLaunchParser.ts`
- Create `minecraft-bot-builder/studio-app/src/simulation/StudioArtifactLoader.ts`
- Create `minecraft-bot-builder/studio-app/src/simulation/StudioSimulationSession.ts`
- Modify renderer IPC/session initialization as required.
- Add tests under `minecraft-bot-builder/studio-app/test/` or the repository’s established TypeScript test location.
- Modify `studio-app/package.json` only if a test command/entrypoint is required.

**Interfaces:**

```ts
export interface StudioLaunchRequest {
  artifactPath: string;
  worldId?: string;
  playbackSpeed: 0.25 | 1 | 4 | 16 | 64;
  autoplay: boolean;
  initialTick?: number;
  finalTick?: number;
  visibleEntityCap?: number;
  evidenceDirectory?: string;
  builderJobId: string;
  mode: "visible" | "evidence";
}
```

`parseStudioLaunchRequest(argv: readonly string[]): StudioLaunchRequest | undefined` returns `undefined` for normal interactive launch with no deterministic simulation arguments.

- [ ] **Step 1: Write failing parser tests** for `--open`, `--world`, `--speed`, `--autoplay`, tick bounds, entity cap, evidence directory, job id and mode.
- [ ] **Step 2: Run Studio tests/typecheck and confirm the parser tests fail.**
- [ ] **Step 3: Implement the typed parser with exact allowed speeds and numeric/path validation.**
- [ ] **Step 4: Extract current `loadArtifact` behavior from `main.mts` into `StudioArtifactLoader` so dialogs and deterministic launch share one implementation.**
- [ ] **Step 5: Add simulation-session initialization that opens the artifact without a dialog, selects replay world/tick, and sends autoplay/speed/tick/entity-cap state to the renderer.**
- [ ] **Step 6: Preserve normal `npm run studio` interactive behavior when no launch request is supplied.**
- [ ] **Step 7: Add evidence-directory/job-correlation metadata to the Studio session. Do not claim screenshot evidence until capture is actually implemented and verified.**
- [ ] **Step 8: Run `npm run typecheck`, `npm test`, `npm run build`, `npm run studio:typecheck`, and `npm run studio:compat`.**
- [ ] **Step 9: Commit:** `feat: add deterministic Builder Studio simulation launch`.

### Task 6: Reconcile skills/docs/PR metadata

**Files:**
- Update `docs/architecture/TAVALL_AI_AGENT_ROLES.md`
- Update `docs/architecture/TAVALL_AI_DISTRIBUTED_EXECUTION.md`
- Update `plugins/tavall-ai/skills/tavall-ai/SKILL.md`
- Update renamed agent skills.
- Update Tavall AI PR #8 title/body/metadata.
- Update Project Novus child PR body with Builder Studio acceptance gates.

- [ ] **Step 1: Search active Tavall AI source/docs for `tavall-ai-agent-` and `tavall-ai-module-builder`; require zero active references except explicit migration history.**
- [ ] **Step 2: Search for wording that claims agents themselves are AI/model runtimes and correct it.**
- [ ] **Step 3: Update PR #8 to describe the corrected names/hierarchy and Builder Studio runner.**
- [ ] **Step 4: Record exact current heads and truthful validation state.**
- [ ] **Step 5: Commit:** `docs: reconcile Tavall agent runtime architecture`.

### Task 7: Exact-head verification and handoff

- [ ] **Step 1: Tavall AI Java 25:** run `./scripts/ci/verify` and `./gradlew --no-daemon clean check stageDistribution` on exact PR #8 head.
- [ ] **Step 2: Project Novus Builder:** run Builder package and Studio Node/TypeScript gates on exact child PR head.
- [ ] **Step 3: Windows Studio:** launch a representative replay through the deterministic CLI path and verify world selection, speed, autoplay and visible simulation.
- [ ] **Step 4: Builder agent acceptance:** from an authorized DEVELOPMENT execution, construct a typed Studio request, launch Studio, and receive session/evidence metadata without arbitrary shell or production mutation authority.
- [ ] **Step 5: Keep both PRs Draft if any exact-head or live acceptance gate is unavailable/failing; record the missing evidence explicitly.
