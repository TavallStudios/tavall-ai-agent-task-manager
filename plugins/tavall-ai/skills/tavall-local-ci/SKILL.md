---
name: tavall-local-ci
description: Use when completing any Tavall repository-backed engineering run that changed source, configuration, tests, scripts, build logic, infrastructure code, or deployable artifacts and exact-head validation is required before completion or PR handoff.
---

# Tavall Local CI

## Core rule

A diff-producing Tavall engineering run is not complete until its **final immutable HEAD** has a durable Tavall `LOCAL_CI` run.

For this ChatGPT installation, CI provenance is `CHATGPT`.

## Required workflow

1. Resolve the repository, active workspace/branch, and exact final commit SHA.
2. Use the Tavall Cloud typed developer-job surface to submit `LOCAL_CI` for that exact SHA with:
   - origin: `CHATGPT`
   - profile: `all`
   - repository/workspace identity from CONTROL
   - normal CONTROL-selected execution provider
3. Do **not** provide caller-authored shell as CI. `LOCAL_CI` must enter through Tavall's typed CI path and repository-owned `scripts/ci/run` / `scripts/ci/verify` contract.
4. Inspect the durable job until it reaches a terminal state when the execution surface permits synchronous follow-through.
5. On failure, inspect Tavall job logs/evidence, classify the failure truthfully, fix the issue when it belongs to the current work, then submit a new `LOCAL_CI` run for the new HEAD.
6. If HEAD changes after a successful run, that success is stale. Validate the new HEAD before claiming completion.

## GitHub checks

Do not manually manufacture GitHub success/status state. The Tavall GitHub bot reconciles durable CONTROL job truth into origin-specific Check Runs and the stable required gate.

A prior successful job for another SHA never satisfies the current SHA.

## Tool discovery

Prefer the direct Tavall Cloud developer workspace/job functions when exposed. If the direct function is not visible, use Tavall Cloud catalog search/describe/invoke to locate the current typed equivalent. Do not replace Tavall execution with GitHub-hosted Actions.

If Tavall Cloud cannot accept the job, report that validation publication is unavailable. Local tests may provide diagnostic evidence, but they do not become a Tavall CI Check Run by assertion.

## Completion evidence

Before claiming the engineering run is validated, retain and report the smallest useful evidence set:

- exact HEAD SHA
- Tavall job ID
- terminal result class
- CI check context
- durable evidence handle

`SOURCE_FAILED` means source validation actually ran and failed. Authorization, dependency, executor, infrastructure, timeout, cancellation, and recovery failures keep their own classifications; never rewrite them as source failures or successes.
