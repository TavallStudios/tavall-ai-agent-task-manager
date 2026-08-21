---
name: tavall-memory-validation
description: Use when validating Tavall AI memory-plane changes, deployments, migrations, cleanup, provider wiring, restart persistence, or tool behavior. Requires exact-head evidence across Postgres, Redis, Qdrant, Graphify, Graphiti, MCP catalog behavior, ordinary-turn no-growth, outbox delivery, and targeted authority/concurrency regressions.
---

# Tavall Memory Validation

Validate the memory plane as a distributed knowledge system, not as a collection of individually green services.

## Tavall Cloud v2 execution plane

When `@Tavall Cloud v2` is available, use it as the default execution and current-evidence plane for substantive Tavall work. Memory answers what Tavall knows; Cloud establishes what is actually checked out, running, deployed, logged, and validated now.

- Bootstrap Cloud with `cloud_dev_session_bootstrap` when current repository/runtime/deployment evidence is needed.
- Use CONTROL-owned workspaces, leases, bounded reads/diffs, sandbox/jobs, and typed Git/GitHub operations instead of raw host paths or unscoped shell access.
- Use Cloud node/service inspection, logs, consoles, and registered lifecycle controls for runtime/deployment evidence.
- If a needed Cloud capability is absent from the frozen direct-tool snapshot, use `cloud_catalog_search` / `cloud_catalog_describe` and `cloud_catalog_invoke`.
- Tavall Cloud is not a memory authority; the existing Postgres/Redis/Qdrant/Graphify/Graphiti and Tavall AI tool boundaries remain unchanged.
- If required evidence is not reachable through a CONTROL-authorized Cloud path, report that as an operational gap rather than bypassing CONTROL.

## Exact-head rule

All acceptance evidence belongs to one exact commit SHA.

If code changes after validation begins:

- mark prior evidence historical;
- rerun the affected checks;
- do not report an older deployed SHA as validation for the new candidate.

## Required validation layers

### 1. Repository quality

Run the repository's current quality process and applicable validation suite. For this repository that normally includes:

- recursive quality-document preflight;
- `./gradlew --no-daemon --max-workers=1 clean check stageDistribution`;
- focused uncached memory integration tests;
- Python seed/cleanup tests and compile checks;
- `git diff --check`.

Run repository/runtime validation through `@Tavall Cloud v2` CONTROL-owned workspaces, sandboxes, jobs, and registered service controls. Do not use GitHub-hosted execution when Tavall policy requires local/Tavall infrastructure, and do not bypass CONTROL with raw host execution.

### 2. Explicit write invariants

Verify through real application/MCP paths:

- ordinary turns do not create durable records;
- `recordMemory` persists explicit authority only;
- cross-project PROJECT supersession is denied;
- cross-thread SESSION supersession is denied;
- supersession cannot silently change scope;
- concurrent writes to one stable memory identity converge rather than producing duplicate active truths;
- supersession removes/replaces the correct semantic representation.

### 3. Transaction/outbox behavior

For an explicit write:

1. Confirm Postgres memory state and semantic outbox obligation commit together.
2. Confirm the outbox drains through the normal `MemorySyncLoopService` path.
3. Confirm Qdrant representation appears only after the committed obligation is processed.
4. For supersession, confirm delete and replacement operations drain correctly.
5. Confirm failed Qdrant delivery queues/retries instead of falling back to an unrelated canonical store.

### 4. Exact cache coherence

Prime an exact-state view before changing memory.

- PROJECT/SESSION writes must refresh the correct current identity after commit.
- GLOBAL writes must invalidate cross-project exact views for the same user/workspace authority through the global revision mechanism.
- Cache failure after canonical commit must not make the canonical write appear rolled back.

### 5. Provider acceptance

Verify each configured provider through the canonical boundary:

- Postgres: exact durable records/history.
- Redis: hot-state/cache behavior.
- Qdrant: profile/dimension/schema, semantic search, retry/outbox delivery.
- Graphify: structural queries and current graph/source evidence.
- Graphiti: temporal search and deterministic `recordTemporalFact`/triplet behavior.

Use `memoryProviderStats` after representative queries to verify calls, degradation count, latency, and context volume.

### 6. Unified memoryContext

After restart, call `memoryContext` for representative tasks and verify:

- exact context appears once;
- semantic context appears once;
- Graphify structural context appears once when configured;
- Graphiti temporal context appears once when configured;
- degraded providers are visible rather than silently omitted;
- current/superseded records are not both presented as active truth.

### 7. Ordinary-turn no-growth

Capture before/after counts around unrelated MCP operations:

- durable Postgres memory count;
- relevant Qdrant collection/point counts;
- queued semantic outbox count.

Ordinary unrelated tool calls must not create durable memory or semantic points.

### 8. Restart/persistence

Restart the exact deployed candidate and verify persistence for:

- explicit Postgres memory;
- prompt-thread continuity;
- Qdrant semantic records;
- Graphiti facts;
- provider health;
- outbox recovery/drain behavior.

Graphify may be rebuilt, but its service/query boundary must remain healthy if configured.

## Cleanup/migration validation

For cleanup or re-embedding work:

- dry-run first;
- inspect candidate classification;
- prove explicit records are excluded from destructive legacy predicates;
- prove fixture/mock profiles cannot contaminate real profiles;
- execute only after the dry-run matches the intended target set;
- rerun the dry-run afterward and require zero remaining candidates.

## Acceptance report

Record at least:

- exact head SHA;
- branch/PR;
- quality manifest/hash where applicable;
- build/test results;
- provider state and telemetry;
- Qdrant collection/point/profile counts;
- outbox state;
- restart/persistence result;
- ordinary-turn no-growth result;
- known non-fatal limitations;
- exact deployed release path/artifact hash when deployed.

Only mark a PR ready when the current candidate head, not an ancestor, owns the acceptance evidence.
