# Tavall Development Memory Seed

This directory is the portable, database-file-equivalent seed for the Tavall AI memory stack.

It intentionally stores source payloads rather than precomputed production vectors:

- `qdrant/documents.ndjson` contains curated organization/repository knowledge for semantic retrieval.
- `graphiti/facts.ndjson` contains verified relationships that can be inserted with Graphiti `add_triplet` without LLM extraction.
- `graphify/repositories.json` lists workspaces whose current source graphs should be built locally. Graphify output is a rebuildable index, not canonical data.

## Dry run

```bash
python3 scripts/import_memory_seed.py --mode mock
```

## Import real semantic data

Install FastEmbed on the dev host:

```bash
python3 -m pip install fastembed
```

Then import into Qdrant and, when configured, Graphiti:

```bash
python3 scripts/import_memory_seed.py \
  --mode real \
  --qdrant-url "$AGENT_TASK_MANAGER_QDRANT_BASE_URL" \
  --graphiti-url "$APP_MEMORY_RUNTIME_GRAPHITI_MCP_ENDPOINT" \
  --execute
```

Real mode writes profile-isolated BGE collections such as:

```text
agent_task_manager_project_tavall_ai_agent_task_manager_knowledge__local_baai_bge_small_en_v1_5_384
```

Documents whose seed metadata has `scope=GLOBAL` are written to the configured
knowledge collection (default `tavall-org`):

```text
agent_task_manager_knowledge_tavall_org_knowledge__local_baai_bge_small_en_v1_5_384
```

Enable the matching runtime settings before acceptance so `memoryContext`
retrieves project semantic and global knowledge candidates separately.

Mock mode writes only to `__fixture_mock_384` collections. Mock vectors are deterministic and useful for transport/filter/import tests, but they are deliberately not presented as semantic BGE data.

## Build Graphify indexes

For each repository in `graphify/repositories.json`, build the current workspace graph from source:

```bash
graphify extract /srv/workspace/tavall-ai-agent-task-manager --code-only --no-cluster
graphify extract /srv/workspace/tavall-project-novus --code-only --no-cluster
```

Use `graphify update <workspacePath> --no-cluster` or `graphify watch <workspacePath>` in active workspaces so structural retrieval tracks the current checkout. Do not treat `graphify-out` as a source of truth.

## Data safety

The seed contains curated repository identities, architecture rules, and memory-system decisions. It intentionally excludes secrets, credentials, private chat transcripts, and raw full-session archives. Raw Git/PR/Codex/session artifacts remain evidence sources fetched only when needed.
