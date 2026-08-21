---
name: tavall-memory-plane
description: Use for substantive Tavall engineering, architecture, debugging, deployment, review, planning, memory investigation, durable memory writeback, and memory-plane validation. Use Tavall AI memory tools for memory and @Tavall Cloud v2 as the CONTROL-authorized execution/current-evidence plane.
---

# Tavall Memory Plane

Use Tavall memory as the context substrate and `@Tavall Cloud v2` as the execution/current-evidence substrate whenever available.

Choose only the modes the task requires: **BOOTSTRAP**, **INVESTIGATION**, **WRITEBACK**, **REVIEW**, **VALIDATION**. Do not fan out merely because tools exist.

## Tavall Cloud v2 execution plane

- Bootstrap with `cloud_dev_session_bootstrap` when current repo/runtime/deployment evidence is needed.
- Use CONTROL-owned workspaces, leases, bounded reads/diffs, sandboxes/jobs, typed Git/GitHub operations, service/node inspection, logs, consoles, and registered lifecycle controls.
- If a needed Cloud capability is absent from the frozen direct snapshot, use `cloud_catalog_search` / `cloud_catalog_describe` and `cloud_catalog_invoke`.
- If required evidence cannot be reached through a CONTROL-authorized path, report an operational gap instead of bypassing CONTROL.
- Cloud is not a memory store. Tavall AI and Postgres/Redis/Qdrant/Graphify/Graphiti keep their existing authority boundaries.

## Authority order

1. Current source/runtime evidence, preferably established through Tavall Cloud v2.
2. Canonical exact Postgres memory.
3. Verified Graphify structural and Graphiti temporal evidence.
4. Qdrant semantic/prior-fix recall.
5. Prompt-thread/history context.
6. Model/chat recollection.

Semantic similarity is a lead, never proof.

## BOOTSTRAP

Call `memoryContext` once early with the narrowest useful `projectId`, `repoPath`, thread/session identity, `queryText`, and already-known metadata. Do not repeat cosmetic variants.

If current state matters, verify the checkout/runtime/deployment through Tavall Cloud v2 before acting on recalled claims. Escalate only when needed.

## INVESTIGATION

Use the smallest necessary sequence:

1. `searchPriorFixes` for known failures/remediations.
2. `searchRelatedContexts` for broader semantic analogues.
3. `memoryRelated` for Graphify topology/ownership/dependencies.
4. `codeImpact` for PR blast radius.
5. `memoryHistory` for Graphiti history/supersession.
6. Reconcile all recall against current source, logs, tests, services, deployment state, and authoritative databases through Tavall Cloud v2 where applicable.

For risky conclusions prefer CURRENT evidence plus one corroborating class. Never use Graphiti as a source index, Graphify as durable history, or Qdrant as canonical truth.

## WRITEBACK

Use `recordMemory` only for a verified, reusable conclusion that improves future decisions. It is a promotion boundary, not transcript logging.

Use the narrowest true scope: `SESSION`, normally `PROJECT`, or sparingly `GLOBAL`. Preserve scope during supersession. Use `recordTemporalFact` only for already-verified chronological relationships.

For engineering changes, prefer provenance with exact commit/PR and relevant Tavall Cloud workspace/job/service/configuration evidence. Never store credentials, raw host paths, transient tool narration, raw chats, unverified semantic matches, temporary progress, or unresolved hypotheses.

## REVIEW

1. Hydrate with `memoryContext` for the PR/task scope.
2. Read current workspace/PR/diff/source and validation evidence through Tavall Cloud v2.
3. Use `codeImpact` for meaningful blast radius.
4. Use `memoryRelated`, `memoryHistory`, or `searchPriorFixes` only when the review needs them.

Review authority boundaries, canonical ownership, transaction/outbox behavior, concurrency, supersession, degradation visibility, retrieval quality, and exact-head validation. Memory never substitutes for reading changed code.

## VALIDATION

All acceptance evidence belongs to one exact commit SHA. If code changes, prior evidence becomes historical.

For `tavall-ai-agent-task-manager`, normally run through Tavall Cloud v2 CONTROL-owned workspaces/jobs/sandboxes:

- recursive quality-document preflight;
- `./gradlew --no-daemon --max-workers=1 clean check stageDistribution`;
- focused uncached memory integration tests;
- Python seed/cleanup tests and compile checks;
- `git diff --check`.

Validate explicit-write invariants, Postgres/outbox atomicity, Qdrant retry/drain, exact cache coherence, Postgres/Redis/Qdrant/Graphify/Graphiti provider behavior, `memoryProviderStats`, unified `memoryContext`, ordinary-turn no-growth, restart persistence, and dry-run-first cleanup/migration behavior.

Use registered Cloud service lifecycle for deployed restart tests. Do not use GitHub-hosted execution when Tavall policy requires local/Tavall infrastructure and do not bypass CONTROL because a component is missing from Cloud inventory.

## Tool selection

- `memoryContext`: compiled hydration.
- `searchRelatedContexts` / `searchPriorFixes`: semantic investigation.
- `memoryRelated` / `codeImpact`: Graphify structural evidence.
- `memoryHistory`: Graphiti temporal evidence.
- `recordMemory`: explicit durable promotion.
- `recordTemporalFact`: deterministic verified temporal write.
- `memoryProviderStats`: provider telemetry.
- `@Tavall Cloud v2`: CONTROL-authorized current-evidence and execution plane.

## Final guardrails

Verify project identity, prefer current evidence over stale recall, never present Qdrant similarity as fact without verification, never persist ordinary chat/tool traffic, do not fan out without reason, invalidate old validation when HEAD changes, and write durable memory only when future decision quality materially improves.
