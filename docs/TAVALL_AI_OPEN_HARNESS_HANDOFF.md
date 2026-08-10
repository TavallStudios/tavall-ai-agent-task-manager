# Tavall AI / Tavall Open Harness Handoff

> **Design principle:** **AI-operable, human-legible.**
>
> **Status:** Architecture handoff and consolidation document. This records accepted direction plus existing implementation inputs. It does not claim that repository renames, migrations, or module moves have already happened.

## Purpose

This document is the starting context for the next Tavall AI architecture conversation. It consolidates the earlier design for a general AI capability layer, the self-extending open harness, the function catalog and auto-populating MCP idea, durable memory, validation, approvals, and the implementation that already exists in TavallStudios repositories.

The goal is not to build another chatbot wrapper. The goal is to make Tavall software increasingly operable by AI through typed, discoverable, auditable capabilities while keeping the same software understandable and usable by human engineers.

The target is a system where an AI can:

1. discover capabilities exposed by Tavall code;
2. call those capabilities through provider-neutral function/tool contracts;
3. search a canonical capability catalog before inventing anything new;
4. synthesize a missing adapter/function when a safe capability does not exist;
5. compile, validate, test, sandbox, classify, and register that capability;
6. invoke it through the same catalog/MCP surface;
7. capture evidence, state, memory, and audit receipts;
8. continue work across sessions without requiring the operator to manually rebuild context.

## Accepted repository direction

### `tavall-ai`

`tavall-ai` is the umbrella/base repository and architecture home for Tavall AI work.

It owns the neutral and reusable AI platform contracts, including:

- model/provider contracts;
- capability/function definitions;
- canonical function schemas;
- function discovery and registration;
- invocation routing;
- MCP catalog/server infrastructure;
- policies and authority metadata;
- audit/evidence contracts;
- shared agent/task contracts where they are not harness-specific;
- memory interfaces and retrieval contracts;
- validation interfaces and reusable verification primitives;
- common provider adapters;
- shared AI configuration and typed protocol models.

It should not become an opinionated mega-agent that owns every workflow. The umbrella provides reusable AI infrastructure and capability primitives.

### `tavall-open-harness`

`tavall-open-harness` remains its own repository because it is expected to become a large, multi-module project.

It is the opinionated execution environment built on top of `tavall-ai` and owns:

- agent loops;
- task intake and decomposition;
- orchestration and worker routing;
- repository workspaces/worktrees;
- memory composition for active tasks;
- approval workflows;
- sandbox execution;
- verification pipelines;
- evidence collection;
- Codex and other coding-agent adapters;
- computer-use/external-runner orchestration;
- profiles and runtime policies;
- self-extending capability construction;
- operator-facing execution workflows.

The dependency direction is one-way:

```text
tavall-open-harness
        |
        v
     tavall-ai
```

`tavall-ai` must not depend on `tavall-open-harness`.

### Workspace layout

The intended local workspace can contain separate repositories beneath one AI workspace folder:

```text
tavall-ai/
  tavall-ai/              # umbrella/base repository
  tavall-open-harness/    # separate repository, its own modules/history
  function-catalog/       # migration source until capability code is absorbed
```

This is a workspace layout, not a requirement to use Git submodules. Prefer Gradle composite builds or published artifacts for cross-repository development. Preserve independent repository histories and avoid making one repository's Git tree secretly own another repository.

## Existing repositories are migration inputs, not throwaway prototypes

### `TavallStudios/tavall-ai-agent-task-manager`

The existing AgentTaskManager repository already implements a large portion of the future `tavall-ai` and `tavall-open-harness` concepts. Do not rewrite it from scratch merely because the final naming/ownership boundary is changing.

Existing capabilities include:

- official MCP Java SDK server surfaces;
- a central MCP runtime over stdio and HTTP;
- typed harness task intake;
- worker routing for code, cleanup, computer-use, and retrieval;
- canonical delegation runs and event timelines;
- Codex execution and repository workspaces;
- brokered MCP/tool bundles;
- approval gates;
- ArchUnit and Spoon validation;
- integration-test and patch-scope validation;
- Postgres, Redis, MongoDB, and Qdrant boundaries;
- domain-separated MCP tool modules;
- artifact, cache, context, orchestration, repo, validation, and vector-memory tools;
- cooperative external-runner/computer-use flows;
- durable prompt-thread memory;
- semantic retrieval and project-scoped vector memory;
- a desktop operator surface;
- a repo-local `plugins/tavall-ai/` integration surface.

Migration work should identify which parts are neutral `tavall-ai` infrastructure and which parts are opinionated `tavall-open-harness` behavior, then move them without losing validated behavior.

### `TavallStudios/function-catalog`

`function-catalog` is especially important because it is already the seed of the auto-populating MCP/capability catalog design.

Current/recovered architecture includes:

```text
ai-core
openai-sdk
claude-sdk
gemini-sdk
mcp-server
```

The `ai-core` design already provides:

- `@AIFunction` annotations for function discovery;
- `@AIParam` parameter metadata;
- `AIFunctionCatalog`;
- registrar-backed registration;
- instance registration;
- package scanning;
- canonical JSON schema generation;
- provider-neutral function definitions;
- provider-neutral invocation routing;
- per-function enabled/disabled state;
- state-file refresh;
- catalog snapshots;
- invocation policy hooks;
- invocation audit hooks;
- structured invocation results.

The MCP implementation/tests already prove that:

- registrar-backed functions can automatically appear in `tools/list`;
- package-scanned functions can automatically appear in `tools/list`;
- listed functions can be called through MCP over stdio;
- disabled functions return structured disabled errors;
- Codex can be configured with the MCP and call a dynamically registered function.

This means the future auto-filling MCP should evolve the existing catalog instead of creating a second incompatible registry.

## The auto-populating MCP

The central idea is that MCP should not require every Tavall capability to be manually hardcoded into one giant server class.

The MCP tool surface should be projected from the canonical Tavall AI capability catalog.

### Discovery flow

```text
Tavall code / adapters / registrars
              |
              v
       capability discovery
              |
              v
      canonical catalog
              |
      +-------+--------+
      |                |
      v                v
 canonical schemas   invocation router
      |                |
      +-------+--------+
              v
         MCP projection
              |
              v
      tools/list + tools/call
```

Sources may include:

- explicit registrars;
- annotated methods;
- package scanning where appropriate;
- generated adapters;
- ServiceLoader/provider modules;
- remote capability providers;
- future code-generation outputs after promotion.

Explicit registration should remain available for dependency-aware or security-sensitive capabilities. Reflection/scanning is a discovery mechanism, not permission to instantiate arbitrary production classes or bypass DI.

### Catalog metadata

Every registered capability should eventually expose enough metadata for both humans and agents to reason about it, including:

- stable capability id;
- human-readable name and description;
- owning repository/module;
- input schema;
- output schema;
- version;
- registration source;
- provider/runtime requirements;
- side-effect classification;
- authority/permission requirements;
- data sensitivity classification;
- network/filesystem/process requirements;
- idempotency characteristics;
- timeout/cost hints;
- validation/evidence policy;
- enabled/disabled state;
- deprecation/replacement metadata.

Do not encode these concepts as undocumented magic strings when an enum/typed contract is practical.

## Self-extending capability generation

The harness should be able to extend its own callable surface, but not through arbitrary `eval`, unreviewed shell snippets, or runtime mutation with no evidence.

The accepted conceptual pipeline is:

```text
Need identified
    |
    v
Capability specification
    |
    v
Search existing catalog
    |
    +---- capability exists ----> invoke existing capability
    |
    v
Generate adapter/function
    |
    v
Compile + static validation
    |
    v
Security / authority classification
    |
    v
Generate and run tests
    |
    v
Sandbox execution
    |
    v
Approval / promotion gate
    |
    v
Register in Tavall AI catalog
    |
    v
Project through MCP
    |
    v
Invoke + capture evidence
```

### Rules

- Search before generation. Duplicate tool surfaces are architecture debt.
- Prefer adapters around existing typed application interfaces over direct reflection into implementation internals.
- Generated capabilities must use normal repository architecture and DI rather than creating a parallel dependency system.
- Generated code is untrusted until it compiles and passes the relevant static/runtime gates.
- Side-effecting capabilities require stronger approval than pure/read-only functions.
- Destructive, production, credential, financial, or player-data operations must never become implicitly callable merely because reflection found a method.
- Registration should be reversible and capabilities must support disable/deprecation state.
- Promotion should leave durable evidence explaining where the function came from and what verified it.

## Capability routing and provider fallback

Earlier harness design established a capability-first router rather than binding workflows directly to one provider or one MCP server.

A requested operation should resolve by capability and policy, for example:

```text
requested capability
      |
      v
policy / authority gate
      |
      v
preferred implementation
      |
      +--> local provider
      |
      +--> remote provider
      |
      +--> approved fallback
      |
      v
structured result + receipt
```

Remote/local fallback must preserve capability semantics. A fallback is not allowed to silently weaken permissions, validation, output structure, or audit requirements.

## Task policy, verification, and output gating

The earlier general harness design had these logical layers:

```text
client adapters
    -> task policy engine
    -> capability-first MCP router
    -> execution
    -> verification pipeline
    -> output gate
    -> revision loop when required
```

This remains the right model.

The harness should be able to require particular tools/verifiers for a task. For example, Java changes can be required to pass the Tavall clean-Java validation path, architecture checks, tests, and repository-specific quality gates before output is considered accepted.

A worker's confident prose is not evidence. Verification results should be machine-readable receipts connected to the exact task/run/artifact/commit where possible.

## Memory model

The original reason a custom harness was less attractive was losing the continuity of ChatGPT memory while gaining custom tool control. Custom plugin/tool access and memory can now coexist, so the design should use both appropriately.

### Memory layers

Treat memory as scoped layers rather than one giant vector soup:

1. **Conversation/product memory**
   - high-level user preferences, long-lived project direction, and continuity supplied by the host AI product where available;
2. **prompt-thread memory**
   - exact thread/task continuity;
3. **project/repository memory**
   - architecture decisions, prior fixes, known failure modes, operational context;
4. **task/run memory**
   - current plan, worker outputs, approvals, failures, evidence;
5. **knowledge/index memory**
   - indexed docs/code/artifacts for semantic retrieval.

Existing AgentTaskManager behavior already provides a useful implementation base:

- explicit or derived `threadKey`;
- exact durable thread lookup;
- thread-scoped semantic recall;
- broader project and knowledge recall;
- Postgres durable interaction state;
- Qdrant semantic memory;
- Mongo artifact/chat bodies;
- Redis hot orchestration state;
- compact snapshots and searchable historical chats.

Do not replace host-product memory with Tavall memory or vice versa. They solve different scopes. The harness should consume available host memory as context while retaining first-party durable task/project memory for reproducibility and portability.

## Persistence responsibilities

The existing split remains sensible:

- **Postgres:** authoritative durable task/run metadata, approvals, policies, capability state, thread indexes, audit records, catalog promotion metadata;
- **Redis:** hot queues, worker heartbeats, locks, counters, ephemeral orchestration projections;
- **MongoDB/object storage:** large artifact bodies, chat/capture payloads, generated files when relational storage is inappropriate;
- **Qdrant:** semantic retrieval vectors plus original chunk payload/metadata;
- **Tavall cache layer:** process-local TTL caching in front of hot reads.

Do not let Redis or Qdrant become authoritative for security, approvals, capability ownership, or durable task state.

## Human operation is still a first-class requirement

The design principle is **AI-operable, human-legible**, not "AI-only".

Most qualities that help agents also help a senior engineer joining the team:

- precise names;
- typed requests/results;
- explicit interfaces;
- discoverable ownership;
- deterministic behavior;
- structured errors;
- idempotent operations;
- generated/canonical schemas;
- architecture docs;
- audit receipts;
- reproducible validation.

Human-facing CLIs/UIs do not need to be the primary interface for every internal system, but a human engineer must be able to inspect, understand, debug, and operate the platform without reverse-engineering an opaque agent-only protocol.

The existing desktop operator work can remain an operator surface, but internal architecture should not require a GUI when typed MCP/API/CLI contracts are sufficient.

## Security and authority model

Capabilities need explicit authority boundaries.

At minimum distinguish:

- read-only;
- local mutation;
- repository mutation;
- external-network mutation;
- process execution;
- sandboxed execution;
- production mutation;
- destructive operation;
- credential/sensitive-data access.

The harness should support one-shot approvals and policy-based standing approvals, with higher-risk actions failing closed when authority is missing.

A generated function cannot self-authorize its own promotion.

Approval evidence should name:

- requested action;
- exact capability/version;
- actor/agent;
- scope;
- target;
- relevant artifact/commit/run;
- policy decision;
- approval identity when required;
- result and rollback/recovery information.

## Sandboxing

Self-generated tools and risky executions should run in bounded environments. The harness should prefer typed sandbox/control paths over arbitrary host shell access.

The sandbox boundary should control:

- filesystem scope;
- network scope;
- credentials;
- process lifetime;
- CPU/memory/time budgets;
- allowed executables/capabilities;
- artifact export;
- audit capture.

A sandbox result must be treated as evidence from a particular environment, not proof that production will behave identically.

## Agent and provider model

`tavall-ai` should remain provider-neutral. OpenAI/Codex, Claude, Gemini, local models, and future providers are adapters over shared contracts rather than architectural owners.

`tavall-open-harness` may define opinionated profiles such as coding, cleanup, retrieval, computer-use, architecture review, or release verification, but the model implementation is selected behind provider contracts.

The provider abstraction should preserve:

- tool/function calling;
- structured outputs;
- streaming where useful;
- usage/cost metadata;
- cancellation/timeouts;
- model capability metadata;
- provider-specific extensions without leaking them into every domain interface.

## Codebase-function access

The long-term target is that Tavall codebases can intentionally expose callable interfaces without every project hand-writing bespoke MCP glue.

Preferred direction:

1. domain code defines normal typed interfaces/handlers;
2. an AI adapter/annotation/registrar declares a safe callable boundary;
3. Tavall AI derives or validates the canonical schema;
4. the capability is registered with ownership/policy metadata;
5. MCP/provider adapters project it outward;
6. the invocation router maps structured input back into the typed boundary;
7. result/evidence is normalized.

Do not annotate random deep implementation methods merely to make them callable. The callable boundary should usually be an adapter over an intentional application operation.

## Interaction with Tavall DI

When capabilities live inside Tavall Java applications, dependency construction should use Tavall DI and normal composition roots.

The catalog may discover capability metadata, but it should not become a second service locator that instantiates arbitrary dependencies behind the application's back.

For generated adapters, prefer:

- generated/registered Tavall DI access;
- constructor-provided dependencies;
- explicit registrars created by the owning composition root.

## Suggested module ownership

Exact names can change after inspection, but the conceptual split should resemble:

### `tavall-ai`

```text
tavall-ai-core
  capability contracts
  provider-neutral agent/model contracts
  invocation/result contracts
  policy/audit contracts

tavall-ai-function-catalog
  annotations
  registrars
  scanning/discovery
  schema generation
  catalog state
  invocation routing

tavall-ai-mcp
  MCP server/client adapters
  catalog projection
  stdio/http transports

tavall-ai-memory
  memory contracts
  retrieval contracts
  shared memory models

tavall-ai-provider-openai
tavall-ai-provider-claude
tavall-ai-provider-gemini
  provider adapters
```

Do not create modules merely to satisfy this diagram. Reuse and migrate the existing code first, then split when ownership is genuinely clearer.

### `tavall-open-harness`

Possible module boundaries:

```text
tavall-open-harness-core
  task intake
  plans
  agent loops
  run state

tavall-open-harness-orchestration
  workers
  delegation
  scheduling

tavall-open-harness-capability-builder
  missing-capability analysis
  adapter generation
  compile/test/promotion pipeline

tavall-open-harness-validation
  approval gates
  verification receipts
  repository-specific validators

tavall-open-harness-sandbox
  isolated execution contracts

tavall-open-harness-repo
  repo/worktree/PR workflows

tavall-open-harness-computer-use
  external runners
  cooperative control

tavall-open-harness-app
  CLI/operator/runtime composition
```

Again, preserve existing working modules where possible rather than performing a ceremonial rename avalanche.

## Migration strategy

The next chat should begin by inspecting all three implementation sources before moving code:

1. `TavallStudios/tavall-ai-agent-task-manager`;
2. `TavallStudios/function-catalog`;
3. `TavallMonoRepo` history only where repository history is needed to understand ownership or lost intent.

Then produce an ownership map:

```text
existing class/module
    -> keep in tavall-ai
    -> move to tavall-open-harness
    -> merge with function-catalog equivalent
    -> compatibility adapter
    -> retire
```

### Migration priorities

1. Establish `tavall-ai` repository/workspace naming and canonical docs.
2. Preserve `AI-operable, human-legible.` at the top of the main README.
3. Move/absorb `function-catalog` capability primitives into the canonical `tavall-ai` catalog rather than rewriting them.
4. Split neutral catalog/provider/MCP/memory contracts from harness-specific orchestration.
5. Establish `tavall-open-harness` as a separate repository consuming `tavall-ai` through a composite build/published modules.
6. Migrate AgentTaskManager harness behavior into `tavall-open-harness` incrementally while maintaining buildable checkpoints.
7. Replace legacy AgentTaskManager naming only when the owning module moves; avoid a blind repository-wide rename.
8. Add capability-generation/promotion as a new vertical slice after the existing catalog + MCP projection is stable.

## First implementation vertical slice

Do not begin with full autonomous self-modification. Prove the architecture with one boring capability.

Example acceptance flow:

1. expose a typed harmless function from a fixture/service;
2. register it through the canonical catalog;
3. confirm it automatically appears in MCP `tools/list`;
4. call it through MCP and a model provider;
5. disable it through catalog state and confirm calls fail structurally;
6. ask the harness for a capability that does not exist;
7. have the harness generate a small adapter in an isolated fixture repository;
8. compile it;
9. run generated tests;
10. classify it read-only;
11. approve/promote it;
12. dynamically include it in the catalog on the next controlled load/reload;
13. call it through MCP;
14. persist the generation, validation, approval, invocation, and result evidence.

That proves the entire concept without allowing an AI to invent `deleteProductionDatabaseBecauseItSeemsHelpful()` on day one.

## Non-goals

- Do not build an opaque autonomous super-agent monolith.
- Do not replace normal application architecture with MCP handlers.
- Do not make every Java method callable.
- Do not let reflection bypass DI or permissions.
- Do not let generated tools self-promote.
- Do not make provider-specific SDK models the Tavall domain model.
- Do not use AI prose as validation evidence.
- Do not require humans to use AI to understand or recover the system.
- Do not duplicate the existing function catalog, memory system, or harness capabilities simply because their current repository names are imperfect.

## Open design questions for the next chat

These should be decided from repo inspection, not guessed in this handoff:

- whether `tavall-ai-agent-task-manager` should be renamed in-place to `tavall-ai` or used as a migration source into a new repository;
- whether `function-catalog` is absorbed entirely or retained temporarily as a published compatibility repository;
- exact capability metadata/policy enums;
- hot-reload semantics for newly promoted capabilities;
- whether generated capability source lives in the target application repo, a generated-adapters repo, or both depending on ownership;
- approval thresholds by capability risk;
- how host-product memory is injected into first-party Tavall memory context without making either layer authoritative for the other;
- model/provider selection policy and cost budgets;
- how much of the existing desktop operator surface remains first-party after the harness split.

## Starting instruction for the next chat

Use this document as the architecture handoff. Inspect the current `TavallStudios/tavall-ai-agent-task-manager` and `TavallStudios/function-catalog` repositories before editing. Preserve existing validated behavior. Build an explicit migration/ownership map before moving modules. Keep `tavall-ai` provider-neutral and reusable; keep `tavall-open-harness` opinionated and dependent on `tavall-ai` only. Evolve the existing function catalog into the canonical auto-populating MCP capability catalog. Maintain typed policy, audit, memory, sandbox, validation, and approval boundaries throughout the migration.
