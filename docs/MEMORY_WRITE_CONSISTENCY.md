# Explicit Memory Write Consistency

`recordMemory` is the only canonical durable-memory write boundary for agent-authored Tavall memory. Ordinary MCP, prompt, resource, and thread interactions remain history/retrieval operations and must not promote durable memory implicitly.

## Authority

Every explicit write is stored with `consent_level=explicit` and `metadata.writeMode=explicit`. The MCP schema does not expose a caller-selectable write-authority label. Older payloads may still deserialize a legacy `consentLevel` field, but the service ignores that value and preserves the explicit-write invariant.

Supersession is an authority-scoped replacement operation, not an arbitrary update by memory id:

- user id and workspace id must match the target memory;
- PROJECT memory can only be superseded from the same project;
- SESSION memory can only be superseded from the same project, chat, and thread;
- GLOBAL memory may be superseded from another project only inside the same user/workspace authority;
- the replacement must preserve the existing memory scope;
- the target must still be active and not tombstoned or already superseded.

A scope migration is a separate lifecycle concern and must not be encoded by supplying another scope to `supersedesMemoryId`.

## Commit and provider ordering

Postgres is canonical. An explicit write uses this ordering:

1. create/update the Postgres memory record and any supersession state;
2. enqueue Qdrant upsert/delete obligations in `semantic_sync_outbox` in the same Postgres transaction;
3. commit Postgres;
4. refresh Redis exact-state state after commit;
5. let `MemorySyncLoopService` drain the durable outbox into Qdrant and retry provider failures.

Qdrant is therefore not mutated inside the canonical Postgres transaction. A failed Postgres commit cannot leave a successful explicit memory only in Qdrant. Conversely, a Qdrant outage does not erase the canonical memory: its durable outbox operation remains available for retry.

## Exact-state cache coherence

Exact-state cache keys include a user/workspace global-memory revision. Writing or superseding GLOBAL memory increments that authority revision after commit. Existing project cache entries become unreachable on the next lookup and naturally expire under the configured hot-state TTL.

This avoids Redis key scans while ensuring a GLOBAL exact memory written from Project A is immediately visible to Project B on its next exact-state read for the same user/workspace authority.

PROJECT and SESSION writes refresh only their current exact-state identity because their visibility does not cross the corresponding scope boundary.

## Semantic placement of GLOBAL records

GLOBAL records are canonical and cross-project in Postgres exact state. The current Qdrant compatibility representation records `metadata.semanticProjectId`, identifying the project collection in which that semantic mirror was written. Supersession uses that stored namespace so an old GLOBAL semantic point can be deleted correctly even when the replacement is recorded from another project.

This compatibility representation does **not** yet provide a dedicated cross-project GLOBAL semantic search namespace. Cross-project GLOBAL correctness currently comes from canonical exact-state hydration. A future global-semantic implementation should use a dedicated user/workspace namespace and reuse one query embedding across project/global/knowledge searches rather than adding another independent embedding pass to the current Qdrant latency path.

## Validation requirements

Before promoting a head that changes this boundary, verify at minimum:

- ordinary interaction text never creates a durable memory;
- explicit writes always persist `consent_level=explicit`;
- cross-project PROJECT supersession is rejected without creating a replacement;
- cross-thread/session SESSION supersession is rejected;
- supersession cannot silently change scope;
- GLOBAL supersession across projects remains constrained to the same authenticated user/workspace;
- GLOBAL writes invalidate previously primed exact-state cache views in other projects;
- semantic delete uses the superseded record's recorded semantic namespace;
- semantic outbox rows are committed with the canonical memory and drained successfully afterward;
- unrelated MCP operations do not grow durable memory or Qdrant point counts;
- restart recovery preserves Postgres memory, prompt history, Qdrant semantic state, and provider knowledge.
