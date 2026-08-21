---
name: tavall-memory-investigation
description: Use for Tavall debugging, root-cause analysis, architecture archaeology, regression investigation, prior-fix discovery, PR impact analysis, or any task where memoryContext is not enough and the agent must correlate semantic recall with Graphify structure, Graphiti history, source evidence, and current runtime state.
---

# Tavall Memory Investigation

Use the memory plane to investigate, not to guess more confidently.

## Entry condition

Start from a `memoryContext` hydration whenever possible. If this skill is invoked directly and no hydration exists for the task, call `memoryContext` first.

## Investigation sequence

Use the smallest sequence that can answer the question:

1. **Prior experience**
   - `searchPriorFixes` for regressions, failure signatures, old reviews, and known remediations.
   - `searchRelatedContexts` for broader semantic analogues.
   - Treat returned chunks as candidate evidence until checked.

2. **Current structure**
   - `memoryRelated` for Graphify-backed current code topology, ownership, dependencies, symbol/file relationships, and source locations.
   - `codeImpact` when the question is specifically about a pull request or blast radius.
   - Follow returned file/line evidence into the current checkout before concluding behavior.

3. **History and supersession**
   - `memoryHistory` for Graphiti temporal facts, architecture evolution, incidents, and superseded approaches.
   - A historical fact can explain why something changed; it does not override current source state.

4. **Exact/current state**
   - Reconcile all recalled material against current repository files, runtime state, logs, tests, deployment metadata, or authoritative databases as appropriate.

## Evidence matrix

For non-trivial conclusions, mentally classify evidence as:

- `CURRENT`: observed in current source/runtime/database state.
- `CANONICAL`: exact durable Tavall memory that is still applicable.
- `STRUCTURAL`: Graphify relationship backed by current workspace extraction.
- `TEMPORAL`: Graphiti fact explaining history/evolution.
- `SEMANTIC`: Qdrant similarity/prior-fix candidate.
- `HISTORICAL`: prompt-thread or older artifact context.

Prefer conclusions supported by CURRENT plus at least one other relevant class when the change is risky.

## Conflict handling

When sources disagree:

- Current runtime/source wins over stale semantic recall.
- A newer verified temporal fact can supersede an older one, but verify the target state.
- Do not average conflicting claims into a compromise.
- Identify the stale or unverified claim and, if the corrected conclusion should survive future tasks, hand off to `tavall-memory-writeback` with evidence.

## Query quality

Good queries name the failure and component:

- `FFA kit NPC command disappeared after module bootstrap refactor`
- `Qdrant explicit memory supersession deleting wrong project namespace`
- `Velocity rank command ownership and current registration path`

Bad queries are vague:

- `FFA`
- `memory`
- `what happened`

## Do not do this

- Do not dump every semantic result into the prompt.
- Do not call all providers merely because they exist.
- Do not use Graphiti as a source-code index or Graphify as durable historical truth.
- Do not record a memory from a single unverified semantic match.
- Do not recursively investigate unrelated repositories unless the evidence establishes a dependency.

## Investigation output

A strong internal conclusion should identify:

- current observed behavior;
- likely root cause;
- evidence supporting it;
- contradictory/stale memory if any;
- affected scope/blast radius;
- next implementation or validation action.

When the conclusion is verified and reusable beyond the current task, invoke `tavall-memory-writeback`.
