---
name: tavall-memory-bootstrap
description: Use at the start or continuation of substantive Tavall engineering, architecture, debugging, deployment, review, or planning work when Tavall AI memory tools are available. Hydrate canonical exact, semantic, structural, and temporal context before relying on chat history or assumptions, then keep retrieval scoped to the active project/repository/task.
---

# Tavall Memory Bootstrap

Use Tavall's memory plane as the default context substrate for substantive Tavall work. Do not treat chat history, model memory, or semantic similarity as canonical truth when the memory plane can resolve the task.

## Required first pass

For a substantive Tavall task, call `memoryContext` once early with the narrowest useful scope:

- `projectId`: canonical project/repository key when known.
- `repoPath`: current checkout/worktree when known.
- `threadKey` and `sessionId`: pass them when the current task is continuing an existing thread/session.
- `queryText`: describe the actual work, not a vague topic. Include the system/component and the decision, regression, or change being attempted.
- `metadata`: include only identity/retrieval context that is already known. Do not manufacture provenance.

Do not call `memoryContext` repeatedly with cosmetic rewordings. One good hydration is better than five noisy ones.

## Authority order

Interpret hydrated context in this order:

1. Current repository/source/runtime evidence.
2. Canonical exact Postgres memory returned by the memory plane.
3. Verified temporal/structural evidence from Graphiti/Graphify.
4. Qdrant semantic recall and prior-fix matches.
5. Prompt-thread continuity and other historical context.
6. Model/chat recollection.

Semantic similarity is a lead, not proof. When semantic memory conflicts with current source/runtime state, verify current state and prefer current evidence.

## Escalation rules

After `memoryContext`, escalate only when the task needs deeper evidence:

- Need current code ownership, dependencies, file/line relationships, or PR blast radius: use the `tavall-memory-investigation` skill and Graphify-backed tools.
- Need why an architecture changed, prior incidents, superseded approaches, or chronological relationships: use the `tavall-memory-investigation` skill and Graphiti-backed tools.
- Need prior fixes or analogous failures: use `searchPriorFixes` / `searchRelatedContexts` through the investigation skill.
- Need to persist a verified conclusion: use `tavall-memory-writeback`. Never improvise a durable write from this skill.
- Need to validate the memory plane itself or a change to it: use `tavall-memory-validation`.

## Scope discipline

Keep retrieval scoped to the work in front of you.

- Do not search every Tavall repository because a broad query happens to return results.
- Do not treat GLOBAL memory as permission to modify unrelated projects.
- Do not widen project scope merely to find more matches.
- Use the active branch/worktree/source state to resolve ambiguity.

## Failure behavior

If `memoryContext` reports a provider as degraded:

- Continue with healthy providers and direct repository/runtime evidence.
- State which provider is degraded when it materially limits the conclusion.
- Do not silently replace Qdrant, Graphify, Graphiti, or Postgres with a different source of truth.
- Do not write compensating durable memory simply because retrieval failed.

## Completion check

Before acting on hydrated memory, ensure:

- the project/repository identity is correct;
- current source/runtime evidence does not contradict the recalled claim;
- semantic matches are not being presented as canonical facts without verification;
- any deeper lookup is justified by the task rather than curiosity.
