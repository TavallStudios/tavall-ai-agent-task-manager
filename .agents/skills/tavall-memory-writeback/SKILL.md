---
name: tavall-memory-writeback
description: Use when a Tavall task has produced a verified reusable conclusion that should survive the current chat, run, branch, or agent. Governs recordMemory, supersession, provenance, scope selection, and recordTemporalFact. Never use for ordinary turn capture, raw logs, speculative findings, or semantic-search results that have not been verified.
---

# Tavall Memory Writeback

Write less memory, but make what survives trustworthy.

`recordMemory` is an intentional promotion boundary, not a transcript logger. Ordinary prompts, tool calls, search results, source files, and agent narration must not become durable memory merely because they occurred.

## Tavall Cloud v2 execution plane

When `@Tavall Cloud v2` is available, use it as the default execution and current-evidence plane for substantive Tavall work. Memory answers what Tavall knows; Cloud establishes what is actually checked out, running, deployed, logged, and validated now.

- Bootstrap Cloud with `cloud_dev_session_bootstrap` when current repository/runtime/deployment evidence is needed.
- Use CONTROL-owned workspaces, leases, bounded reads/diffs, sandbox/jobs, and typed Git/GitHub operations instead of raw host paths or unscoped shell access.
- Use Cloud node/service inspection, logs, consoles, and registered lifecycle controls for runtime/deployment evidence.
- If a needed Cloud capability is absent from the frozen direct-tool snapshot, use `cloud_catalog_search` / `cloud_catalog_describe` and `cloud_catalog_invoke`.
- Tavall Cloud is not a memory authority; the existing Postgres/Redis/Qdrant/Graphify/Graphiti and Tavall AI tool boundaries remain unchanged.
- If required evidence is not reachable through a CONTROL-authorized Cloud path, report that as an operational gap rather than bypassing CONTROL.

## Write only when all are true

A durable write should be:

- verified by current source/runtime/test/evidence;
- reusable beyond the current turn;
- materially helpful to a future agent;
- concise enough to retrieve cleanly;
- scoped to the authority that should see it;
- backed by a useful provenance reference when one exists.

If any of those are false, do not write durable memory.

## Good write candidates

- verified architecture ownership or invariant;
- confirmed root cause and durable fix pattern;
- project state that future work must respect;
- intentional human correction or preference relevant to the project;
- incident outcome with evidence;
- explicit supersession of a formerly correct fact;
- stable deployment/topology fact whose lifetime extends beyond the current run.

## Bad write candidates

- raw chat text or whole summaries;
- tool-call narration;
- unverified semantic matches;
- temporary build progress;
- one-off command output;
- secrets, credentials, tokens, or sensitive raw payloads;
- facts already represented canonically elsewhere when a durable memory adds no retrieval value;
- "maybe", "probably", or unresolved hypotheses.

## Choose scope deliberately

Use the narrowest durable scope that matches the truth:

- `SESSION`: valid only for the current durable thread/session identity.
- `PROJECT`: applies to one project/repository. This is the normal default for engineering memory.
- `GLOBAL`: applies across projects for the same Tavall memory authority. Use sparingly for organization-wide conventions, provider ownership, or truly cross-project facts.

Never use GLOBAL merely because a fact feels important.

## recordMemory payload

Provide:

- `projectId`: current project identity, including when writing GLOBAL memory so semantic provenance remains traceable.
- `title`: stable concise identity for the memory. Reuse the same title for updates to the same stable fact.
- `summary`: distilled claim/conclusion.
- `facts`: optional short supporting facts, not an evidence dump.
- `importance`: use high values only for facts that should routinely enter exact context.
- `sensitivity`: normally `internal` unless the data genuinely requires another supported label.
- `sourceReference`: prefer stable evidence such as commit SHA, PR, issue, validation run, deployment release, source path, or incident reference.
- `supersedesMemoryId`: only when intentionally replacing an existing active memory of the same scope and authority.
- `metadata`: structured provenance that improves future verification. Do not duplicate the prose body.

Do not attempt to set the write authority to implicit. `recordMemory` is explicitly authoritative by definition.

## Supersession rules

Before superseding:

1. Retrieve/identify the existing memory being replaced.
2. Verify it belongs to the same authority envelope.
3. Preserve scope: SESSION replaces SESSION, PROJECT replaces PROJECT, GLOBAL replaces GLOBAL.
4. Explain the replacement in the new memory and include evidence.
5. Use the actual `supersedesMemoryId`; do not create a second unrelated title and leave contradictory active memories when the relationship is known.

If scope needs to change, treat it as a deliberate migration, not a supersession shortcut.

## Temporal facts

Use `recordTemporalFact` only for already-verified relationships where chronology/history matters, for example:

- `hash embeddings` `SUPERSEDED_BY` `local BGE profile`
- `PR #22` `INTRODUCED` `explicit memory plane`
- `release X` `DEPLOYED_TO` `dev-storage`

Do not ask Graphiti to infer a relationship that Tavall already knows structurally. Use deterministic triplets for known facts.

## Evidence first

Preferred provenance strength:

1. exact commit/release/test/runtime evidence;
2. PR/issue with acceptance evidence;
3. current source path + observed behavior;
4. verified Graphify/Graphiti relationship;
5. semantic/prior-fix context corroborated by current evidence.

A Qdrant match alone is never sufficient write authority.

## Final writeback check

Before calling a write tool, confirm:

- Would a future agent make a better decision because this exists?
- Is the claim verified now?
- Is the scope correct?
- Is the title stable enough to update later?
- Is there a provenance reference?
- Am I replacing an old truth that should be superseded?
- Am I accidentally storing raw conversation or transient state?

If the answer to the last question is yes, do not write.
