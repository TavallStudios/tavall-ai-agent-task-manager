---
name: tavall-memory-review
description: Use for independent Tavall pull-request review, architecture review, regression review, or blast-radius analysis when the reviewer should combine memoryContext with codeImpact, Graphify structure, Graphiti history, prior fixes, current diff/source, and acceptance evidence rather than reviewing the patch in isolation.
---

# Tavall Memory Review

Review the change against the system Tavall actually has, not only the diff somebody hopes is correct.

## Tavall Cloud v2 execution plane

When `@Tavall Cloud v2` is available, use it as the default execution and current-evidence plane for substantive Tavall work. Memory answers what Tavall knows; Cloud establishes what is actually checked out, running, deployed, logged, and validated now.

- Bootstrap Cloud with `cloud_dev_session_bootstrap` when current repository/runtime/deployment evidence is needed.
- Use CONTROL-owned workspaces, leases, bounded reads/diffs, sandbox/jobs, and typed Git/GitHub operations instead of raw host paths or unscoped shell access.
- Use Cloud node/service inspection, logs, consoles, and registered lifecycle controls for runtime/deployment evidence.
- If a needed Cloud capability is absent from the frozen direct-tool snapshot, use `cloud_catalog_search` / `cloud_catalog_describe` and `cloud_catalog_invoke`.
- Tavall Cloud is not a memory authority; the existing Postgres/Redis/Qdrant/Graphify/Graphiti and Tavall AI tool boundaries remain unchanged.
- If required evidence is not reachable through a CONTROL-authorized Cloud path, report that as an operational gap rather than bypassing CONTROL.

## Review bootstrap

1. Call `memoryContext` with the PR/task goal, project, repository path, and relevant thread identity.
2. Read the current PR/diff/source and validation evidence.
3. Use `codeImpact` for Graphify blast radius when reviewing a PR with meaningful behavioral change.
4. Use `memoryRelated` when ownership/dependency relationships are ambiguous.
5. Use `memoryHistory` when the change modifies a previously superseded architecture or revives an old approach.
6. Use `searchPriorFixes` when the patch touches a known regression/failure domain.

Do not substitute memory tools for reading the actual changed code.

## Review lenses

Check at least the lenses that apply:

### Authority

- Can one project/user/workspace/thread mutate another authority's state?
- Are IDs resolved through scoped queries or bare identifiers?
- Does GLOBAL really mean global only inside the intended authority envelope?

### Canonical ownership

- Is Postgres still canonical for durable relational memory/state?
- Is Redis only hot/ephemeral?
- Is Qdrant still associative rather than source-of-truth?
- Are Graphify and Graphiti treated as rebuildable/curated providers rather than hidden canonical stores?

### Transaction boundaries

- Are external/provider/cache side effects happening before canonical commit?
- Can rollback leave phantom Redis/Qdrant/Graphiti state?
- Is an outbox or post-commit boundary used where needed?

### Concurrency

- Can two correct agents create duplicate active truths?
- Are stable identities serialized at the canonical datastore?
- Can supersession races create conflicting active records or deadlocks?

### Supersession/lifecycle

- Is replacement scoped to the correct active record?
- Is old semantic state deterministically removed or superseded?
- Can a scope change be smuggled through supersession?
- Do historical facts remain explainable without remaining active context?

### Degradation

- Are provider failures visible rather than silently converted to success/empty results where strict behavior is required?
- Does failure of a noncanonical cache/index incorrectly make a committed canonical write look failed?

### Retrieval quality

- Is context compiled once and ranked instead of repeatedly dumping provider output?
- Are semantic results treated as candidates rather than facts?
- Is cross-project/global retrieval intentionally scoped?

## Evidence requirement

A review approval should be based on:

- current source/diff;
- relevant architecture/quality rules;
- current Graphify/Graphiti/memory evidence when applicable;
- targeted regressions for the discovered failure classes;
- current-head validation, not validation from an older commit.

If review changes the branch, invalidate prior exact-head acceptance claims until the new head is rerun.

## Findings

Prioritize findings by behavioral risk:

1. correctness/security/authority/data-loss issues;
2. concurrency/transaction consistency;
3. architecture ownership regressions;
4. silent degradation or stale-state behavior;
5. missing regression coverage;
6. maintainability/documentation issues.

Do not manufacture low-value style findings to make a review look busy.

## Memory writeback after review

Use `tavall-memory-writeback` only when the review establishes a reusable verified fact, such as a newly identified architecture invariant or a corrected failure pattern. Do not store every review comment.
