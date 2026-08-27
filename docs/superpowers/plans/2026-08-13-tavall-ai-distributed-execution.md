# Tavall AI Distributed Execution Implementation Plan

> Execute this plan on `working/tavall-ai-distributed-execution-runtime`, stacked on `working/tavall-ai-role-runtime` / PR #4.

## Goal

Restore the Tavall AI distributed AI-call architecture so worker/session scheduling is not conflated with AI provider/execution routing. Introduce a first-class distributed-execution module, establish node and ChatGPT Web runtime identities, clarify the bootstrap/runtime/module split, and make Builder the first domain-agent acceptance consumer.

## Task 1: Lock the distributed execution contract with tests

Create focused tests before production implementation for:

- deterministic node-target selection;
- ChatGPT Web fallback when a preferred node target returns a retryable failure;
- required capability filtering;
- explicit surface constraints;
- non-retryable failure stop;
- bounded attempts;
- no eligible target terminal result;
- ordered attempt evidence.

The tests must use in-memory fake target providers only. No Cloud, browser, Codex or network process is required.

## Task 2: Add `tavall-ai-module-distributed-execution`

Implement provider-neutral types and router:

- `TavallAIExecutionSurface`;
- `TavallAIExecutionTarget`;
- `TavallAIExecutionRequest`;
- `TavallAIExecutionAttempt`;
- `TavallAIExecutionResult`;
- `TavallAIExecutionTargetProvider`;
- `TavallAIDistributedExecutionRouter`.

The router receives only already-authorized targets from providers and never creates/widens infrastructure authority.

## Task 3: Correct scheduler ownership

Update scheduler role provider, instructions and plugin skill:

- worker/top-level-session placement only;
- no AI provider/model/web routing ownership;
- scheduler may narrow eligible execution locations but distributed execution selects the AI execution surface/provider;
- preserve Cloud authority and durable job/session recovery.

## Task 4: Establish runtime identities

Expand the common runtime identity model to include `CHATGPT_WEB` alongside `NODE_AGENT`.

Add a web runtime host boundary analogous to the node host boundary. It owns web session/conversation mechanics behind an authorized host adapter and does not reuse Tavall Cloud's inbound ChatGPT-to-CONTROL adapter as an executor.

## Task 5: Document bootstrap/runtime/module migration

Update Tavall AI architecture docs and settings/build comments to make the target layering explicit:

- bootstrap = composition/discovery;
- runtime = process identity;
- module = loadable role/domain behavior;
- provider adapter = concrete model/process backend.

Do not perform the cross-repository Function Catalog migration in this PR. Record it as the next stacked implementation boundary so current source remains buildable while ownership is corrected deliberately.

## Task 6: Add Builder domain-module contract

Add `tavall-ai-module-builder` as a thin Tavall AI domain module, not a duplicate Minecraft builder implementation.

Its contract should identify the existing Builder roles/skill families and the external artifact/validation boundary:

- Planner;
- Terrain;
- Architecture;
- Detail;
- Repair;
- Visual Critic;
- BuildSpec / `.schem` / replay / visual evidence references.

Builder may request distributed AI execution for ambiguous/visual/model work while existing `minecraft-bot-builder` remains authoritative for world/schematic/simulation implementation.

## Task 7: Update role/plugin architecture

Update top-level Tavall AI skill and architecture docs so agents/runtime consumers understand:

- roles are modules, not AI processes;
- distributed calls are a module capability;
- executable tools such as GitHub CLI remain Cloud-granted execution capabilities;
- Function Catalog remains typed function/MCP infrastructure;
- Builder is the first domain-agent composition case.

## Task 8: Verification

Run when a Java 25 Tavall workspace is available:

```text
./scripts/ci/verify
./gradlew --no-daemon clean check stageDistribution
```

Then perform non-production acceptance:

1. authorized node target success;
2. node retryable failure -> ChatGPT Web fallback;
3. explicit web-only request;
4. revoked/stale target omitted by host provider;
5. no eligible target fail-closed;
6. Builder task invoking a distributed model call and returning an artifact/evidence reference without moving Builder world implementation into Tavall AI.

Do not claim Java/runtime acceptance from connector-only source edits.
