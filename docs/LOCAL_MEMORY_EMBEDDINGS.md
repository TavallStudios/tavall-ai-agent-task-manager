# Local Memory Embeddings

Tavall AI semantic memory uses local CPU embeddings by default. The default path does not require Gemini, OpenAI, or another paid embedding API.

## Default runtime

The default embedding profile is:

- provider: `local`
- model: `BAAI/bge-small-en-v1.5`
- dimensions: `384`
- runner: `scripts/fastembed_embed.py`
- vector store: Qdrant

Install FastEmbed on the host running Tavall AI:

```bash
python3 -m pip install fastembed
```

The local runner uses FastEmbed/ONNX and does not require a GPU.

The runtime fails the embedding operation when the configured local provider cannot run. It does not silently mix hash embeddings into a BGE collection. `hash` remains available as an explicit operator-selected provider for degraded or diagnostic use.

To override the default provider order:

```bash
export AGENT_TASK_MANAGER_EMBEDDING_PROVIDER_ORDER=local
```

Gemini remains supported when intentionally configured:

```bash
export GEMINI_API_KEY=...
export AGENT_TASK_MANAGER_EMBEDDING_PROVIDER_ORDER=gemini
export AGENT_TASK_MANAGER_EMBEDDING_DIMENSIONS=1536
```

## Embedding-profile collection isolation

Qdrant collection dimensions and vector spaces are fixed properties of the embedding profile. A 384-dimensional BGE vector must not be written to or queried against a collection created for a 1536-dimensional Gemini embedding.

Project and knowledge collections therefore include an embedding-profile suffix. For example:

```text
agent_task_manager_project_project_novus_tasks__local_baai_bge_small_en_v1_5_384
```

Existing unsuffixed collections are left untouched. The legacy `app.qdrant.collection` is also kept unsuffixed for migration compatibility.

This makes embedding-model changes non-destructive and gives operators a clean rollback path.

## Re-embed existing Qdrant memory

`scripts/reembed_qdrant_local.py` copies existing semantic payloads into new profile-isolated collections and re-embeds their original chunk text locally.

The migration is a dry-run unless `--execute` is supplied.

Review the discovered source and target collections:

```bash
python3 scripts/reembed_qdrant_local.py \
  --qdrant-url "$AGENT_TASK_MANAGER_QDRANT_BASE_URL"
```

Perform the migration:

```bash
python3 scripts/reembed_qdrant_local.py \
  --qdrant-url "$AGENT_TASK_MANAGER_QDRANT_BASE_URL" \
  --execute
```

By default the script discovers unsuffixed collections beginning with:

- `agent_task_manager_project`
- `agent_task_manager_knowledge`

Migrate a specific collection, including the older legacy collection, with:

```bash
python3 scripts/reembed_qdrant_local.py \
  --qdrant-url "$AGENT_TASK_MANAGER_QDRANT_BASE_URL" \
  --source agent_task_manager_context_v2 \
  --execute
```

The migration:

- never deletes a source collection;
- preserves point ids and existing payload metadata;
- reads `chunkText`, falling back to `body` when necessary;
- writes embedding provider/model/dimension provenance into the migrated payload;
- uses the same local embedding profile naming convention as the Java runtime;
- is safe to dry-run before any writes.

After migration, validate semantic retrieval against representative project, prior-fix, knowledge, and prompt-thread queries before retiring any old embedding collections.

## Relevant environment variables

```text
AGENT_TASK_MANAGER_EMBEDDING_PROVIDER_ORDER
AGENT_TASK_MANAGER_EMBEDDING_DIMENSIONS
AGENT_TASK_MANAGER_LOCAL_EMBEDDING_COMMAND
AGENT_TASK_MANAGER_LOCAL_EMBEDDING_MODEL
AGENT_TASK_MANAGER_LOCAL_EMBEDDING_TIMEOUT_SECONDS
AGENT_TASK_MANAGER_QDRANT_BASE_URL
AGENT_TASK_MANAGER_QDRANT_API_KEY
```

The model and dimension together define the semantic vector space. Changing either should create/use a different embedding-profile collection rather than mutating an existing collection in place.
