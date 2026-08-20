# Memory Knowledge Plane

AgentTaskManager owns memory identity, policy, continuity, retrieval, and context compilation. Graphify, Graphiti, and Qdrant are replaceable retrieval providers beneath that Java-owned boundary.

## Provider responsibilities

| Provider | Responsibility | Canonical? |
| --- | --- | --- |
| Postgres | Durable exact memory, task/thread state, policy and metadata | Yes for its owned relational state |
| Redis | Hot working-memory cache and ephemeral runtime state | No |
| Qdrant | Associative semantic recall, prior fixes, related context | No |
| Graphify | Current code topology, file/line relationships, PR blast radius | No; rebuildable from Git workspaces |
| Graphiti | Curated temporal facts, supersession, incidents and architecture evolution | No; knowledge index with provenance |
| Git/PR/provider session files/logs | Raw evidence fetched by reference | Source evidence |

No provider is allowed to silently become another provider's source of truth.

## Java boundary

External providers implement `MemoryKnowledgeProvider` and return `MemoryKnowledgeContext`. Agent code consumes Tavall concepts rather than vendor APIs.

`MemoryRetrievalService.lookup(...)` is the canonical hydration path:

1. resolve memory identity;
2. load exact state through the existing Redis/Postgres path;
3. retrieve project-scoped Qdrant semantic candidates and, when enabled, the configured global knowledge collection;
4. compile configured Graphify and Graphiti context;
5. keep degraded providers visible in the hydration section;
6. record provider retrieval telemetry.

Focused MCP tools remain available for deeper agent-directed lookup:

- `memoryContext`
- `memoryRelated`
- `codeImpact`
- `memoryHistory`
- `recordTemporalFact`
- `memoryProviderStats`

## Graphify

Graphify remains adjacent to the workspace, not inside the Java runtime. Build and refresh `graphify-out/graph.json` from the active checkout. The graph is an index and can be deleted/rebuilt without losing canonical Tavall state.

Typical local setup:

```bash
uv tool install "graphifyy[mcp]"
graphify extract /srv/workspace/tavall-ai-agent-task-manager --code-only --no-cluster
graphify-mcp \
  /srv/workspace/tavall-ai-agent-task-manager/graphify-out/graph.json \
  --transport http \
  --host 127.0.0.1 \
  --port 18999
```

The current Graphify CLI exposes incremental refresh as `graphify update <workspacePath> --no-cluster`; `graphify --update <workspacePath>` is not a valid command for the installed CLI. Keep the MCP server bound to loopback unless its bearer API key is configured.

For a shared workspace server, use Graphify's `project_path` support and protect non-loopback HTTP with its bearer API key.

Keep graphs current with incremental update, watch mode, or workspace lifecycle hooks. Tavall should eventually trigger refresh automatically on workspace attach/checkout/commit boundaries.

## Graphiti

Graphiti runs as an external MCP sidecar. It receives only high-value changing knowledge:

- architecture decisions;
- verified regression/root-cause findings;
- incident outcomes;
- meaningful deployment/topology changes;
- superseded approaches;
- important human corrections;
- verified agent discoveries that should survive the originating task.

Do not send every chat message, source file, tool call, commit, or test log.

Already-known structured facts use `add_triplet` through `recordTemporalFact`; this avoids paying an extraction model to rediscover a relationship Tavall already has in structured form.

## Configuration

Spring relaxed binding exposes the memory runtime properties through standard environment names:

```text
APP_MEMORY_RUNTIME_GRAPHIFY_MCP_ENDPOINT
APP_MEMORY_RUNTIME_GRAPHIFY_API_KEY
APP_MEMORY_RUNTIME_GRAPHITI_MCP_ENDPOINT
APP_MEMORY_RUNTIME_GRAPHITI_API_KEY
APP_MEMORY_RUNTIME_GRAPHITI_GROUP_ID
APP_MEMORY_RUNTIME_EXTERNAL_PROVIDER_TIMEOUT
APP_MEMORY_RUNTIME_EXTERNAL_CONTEXT_LIMIT
APP_MEMORY_RUNTIME_GRAPHIFY_DEPTH
APP_MEMORY_RUNTIME_GRAPHIFY_TOKEN_BUDGET
```

Provider endpoints default to blank. With both blank, the runtime behaves like the existing exact + Qdrant semantic memory system.

Recommended development values:

```bash
export APP_MEMORY_RUNTIME_GRAPHIFY_MCP_ENDPOINT=http://127.0.0.1:18999/mcp
export APP_MEMORY_RUNTIME_GRAPHITI_MCP_ENDPOINT=http://127.0.0.1:8000/mcp
export APP_MEMORY_RUNTIME_GRAPHITI_GROUP_ID=tavall
```

## Seed data

`seed/tavall-memory-dev` is the portable development seed. It stores payloads, not production vectors.

```bash
# Inspect only; deterministic mock vectors, no writes.
python3 scripts/import_memory_seed.py --mode mock

# Real local BGE import.
python3 -m pip install fastembed
python3 scripts/import_memory_seed.py \
  --mode real \
  --qdrant-url "$AGENT_TASK_MANAGER_QDRANT_BASE_URL" \
  --graphiti-url "$APP_MEMORY_RUNTIME_GRAPHITI_MCP_ENDPOINT" \
  --execute
```

Real semantic collections use the same `local_baai_bge_small_en_v1_5_384` profile as the Java runtime. Mock mode writes only to `fixture_mock_384` collections so deterministic test vectors cannot contaminate semantic memory. The importer validates every source entry, requires exact 384-dimensional vectors, validates existing Qdrant schemas before writing, and uses deterministic point IDs; rerunning a seed is therefore an upsert rather than a second copy. Graphiti seed facts use a scoped `search_memory_facts` check before deterministic `add_triplet` insertion.

## Legacy-data reconciliation

The explicit memory boundary does not retroactively reinterpret old provider data. Audit and cleanup are therefore a separate, dry-run-first operation:

```bash
python3 scripts/cleanup_legacy_memory.py \
  --qdrant-url "$AGENT_TASK_MANAGER_QDRANT_BASE_URL" \
  --postgres-url "$DB_URL" \
  --postgres-user "$DB_USER"

python3 scripts/cleanup_legacy_memory.py \
  --qdrant-url "$AGENT_TASK_MANAGER_QDRANT_BASE_URL" \
  --postgres-url "$DB_URL" \
  --postgres-user "$DB_USER" \
  --execute
```

The tool scans only real `__local_baai_bge_small_en_v1_5_384` collections. It removes confirmed pre-explicit MCP/prompt/worker/bridge capture points and non-explicit `memory-*` or legacy `mem_*` points, never fixture collections or points marked `writeMode=explicit`. Postgres deletion is restricted to implicit records linked to the former heuristic mutation actions; explicit records are retained. The execute path deletes the corresponding semantic-sync mutations in the same transaction and clears only Redis `tavall-ai:memory-runtime:working:*` exact-state keys. A post-cleanup app restart clears process-local semantic caches and rebuilds exact state from Postgres.

## Evidence and full conversations

Do not duplicate complete Codex or provider conversations merely to make memory self-contained. Store the distilled finding, stable identity/scope, evidence references, and enough provenance to recover the source. Read the provider/session file only when an agent genuinely needs verbatim history.

## Failure and rollback

Graphify and Graphiti are optional. Blank endpoints disable them. Configured provider failures return explicit degraded context while exact retrieval continues independently. A configured Qdrant failure is never converted to the in-process store: memory hydration reports the Qdrant provider as degraded and semantic writes remain queued for retry by the existing outbox path.

Rollback is therefore configuration-first:

1. blank the Graphify/Graphiti endpoint;
2. restart the runtime;
3. retain existing Postgres/Qdrant memory;
4. remove/rebuild external indexes separately if needed.

Seed Qdrant collections are profile-isolated and source collections are never implicitly deleted.

## Local dev service deployment

The dev host uses the checked-in `deploy/agenttaskmanager.service` template. The
service runs the staged Gradle distribution from the rollback-friendly
`/srv/AgentTaskManager/current` symlink. Keep the existing root-owned
`/etc/agenttaskmanager.env` and `/etc/tavall/tavall.env` files intact; install
`deploy/agenttaskmanager-memory-plane.env.example` as the separate
`/etc/agenttaskmanager-memory-plane.env` fragment. The application accepts the
existing `DB_URL`, `DB_USER`, and `DB_PASS` names as datasource aliases.

After staging a release, copy the distribution, `docs/`, `mcp-servers/`,
`scripts/fastembed_embed.py`, and the seed tooling under a versioned release
directory, advance `current`, run `systemctl daemon-reload`, and restart
`agenttaskmanager.service`. The docs tree is required by the existing context
and clean-Java MCP tools. Import the real seed only after Qdrant,
Graphify, and Graphiti have passed their health probes. Revert `current` to the
previous release and restart the unit to roll back the application; external
indexes remain independently rebuildable.

On the dev host, install `deploy/graphify-memory-plane.service`,
`deploy/graphiti-memory-plane.service`, and `deploy/graphiti-start.sh` under
`/etc/systemd/system` and `/srv/AgentTaskManager/sidecars` respectively. The
Graphiti source/virtualenv is an external sidecar installation, not a Git
dependency of AgentTaskManager. Keep the Gemini credential in the root-owned
`/etc/tavall/graphiti-memory-plane.env` (or provide `GEMINI_API_KEY` through
the existing service environment), then enable both units after FalkorDB is
available.
The named dev Qdrant and FalkorDB containers should use Docker's
`unless-stopped` restart policy; their data remains external to the release
symlink and is never part of the application artifact.

## Validation before promotion

Before this feature leaves Draft status:

- run the full Gradle check suite;
- run ArchUnit and Spoon architecture/source-shape validation;
- run the real HTTP MCP provider tests;
- boot Graphify against a real Tavall workspace and verify query + PR-impact retrieval;
- boot Graphiti and verify search + `add_triplet` against the configured graph backend;
- import the seed into a disposable/dev Qdrant using real BGE vectors;
- verify representative exact, semantic, structural, temporal, prior-fix, and prompt-thread hydration;
- inspect provider telemetry and verify outages are reported as degraded rather than silently empty.
