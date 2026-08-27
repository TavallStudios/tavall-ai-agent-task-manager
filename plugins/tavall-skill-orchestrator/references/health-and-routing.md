# Tavall Skill Health and Routing

## Health algorithm

For each Tavall request:

1. Build a candidate route from prompt intent and current repository/runtime context.
2. Add cross-cutting foundations: memory when available, Git policy when repository/PR state may change, and exact-head completion policy when engineering work produced a diff.
3. Discover exact installed identities and callable runtime capabilities.
4. Detect duplicate exact skill identities before load order can silently select one.
5. Resolve dependencies topologically.
6. Assign `AVAILABLE`, `DEGRADED`, `MISSING`, `SKIPPED_BY_SCOPE`, or `BLOCKED`.
7. Execute only after required policy dependencies have a safe state.
8. Re-run the health gate if the task crosses into a new domain mid-run.

## Memory behavior

The orchestrator routes Tavall prompts through `tavall-memory-plane` when exposed; the memory skill decides provider fan-out. For substantive Tavall work, if the memory skill is known but `memoryContext`/equivalent is not exposed:

```text
memory = DEGRADED
fallback authority = current repository + runtime + tests + logs
forbidden = claiming memory bootstrap succeeded
```

A missing memory provider should not block safe work when current evidence is sufficient, but the missing context must remain visible when it could affect correctness.

## Git behavior

If Git or GitHub state may be mutated, `tavall-git-workflow` is a policy dependency. If it cannot be loaded, read the current canonical repository workflow directly when possible and mark the skill `DEGRADED`; otherwise block consequential Git policy decisions.

## Tavall AI agent-family behavior

For substantive repository work, prefer this shape when the installed Tavall AI plugin exposes it:

```text
tavall-skill-orchestrator
-> tavall-memory-plane when available
-> tavall-git-workflow when repository/PR state is involved
-> tavall-ai
-> tavall-agent-orchestration
-> smallest acceptance-unit specialists
-> tavall-local-ci / typed Tavall Cloud LOCAL_CI when a diff exists
-> independent review / E2E / docs as required by acceptance
-> memory writeback when verified and reusable
```

`agent-task-manager` is the narrower harness/task-runtime specialist. It is not the top-level Tavall AI router and its prompt-thread context does not silently replace the Tavall memory-plane foundation.

Use `tavall-agent-scheduler` only for a real distributed placement/recovery/isolation/resource boundary. Use `tavall-ai-distributed-execution` only to route one bounded model call across already-authorized providers/runtimes.

## Exact-head completion behavior

For a diff-producing Tavall engineering run:

```text
final HEAD changes -> prior CI evidence becomes stale
final HEAD -> tavall-local-ci when installed
          -> typed Tavall Cloud LOCAL_CI equivalent when skill is degraded
          -> no completion claim when neither path exists
```

The evidence should retain the exact HEAD, durable job ID, result class, check context, and evidence handle. Infrastructure/authorization/executor failures keep their own classifications and are not rewritten as source failures.

## Route examples

### Java implementation

```text
tavall-skill-orchestrator
-> tavall-memory-plane
-> tavall-git-workflow
-> tavall-ai -> tavall-agent-orchestration -> tavall-agent-implementation
-> tavall-java-tools
-> tavall-local-ci / typed Tavall Cloud LOCAL_CI
-> tavall-agent-review
-> memory writeback if verified/reusable
```

### Web/UI work

```text
tavall-skill-orchestrator
-> tavall-memory-plane
-> tavall-git-workflow when source/PR state changes
-> tavall-ai -> tavall-agent-orchestration when repository work is substantive
-> tavall-web-agent (discover exact identity)
-> impeccable when available
-> browser/responsive visual acceptance
-> tavall-local-ci / typed Tavall Cloud LOCAL_CI when a repo diff exists
-> memory writeback if verified/reusable
```

### Builder Studio work

```text
tavall-skill-orchestrator
-> tavall-memory-plane
-> tavall-git-workflow when repository state changes
-> tavall-ai -> tavall-agent-orchestration -> tavall-agent-builder when AI coordination is needed
-> minecraft-builder
-> rendering-builder-replays only when replay/render verification is in scope
-> real Builder Studio/render/bot evidence
-> tavall-local-ci / typed Tavall Cloud LOCAL_CI when a repo diff exists
-> memory writeback if verified/reusable
```

### PR/staging reconciliation

```text
tavall-skill-orchestrator
-> tavall-memory-plane
-> tavall-git-workflow
-> tavall-ai -> tavall-agent-orchestration -> tavall-agent-reconciliation
-> inspect live PR/staging graph
-> repair only affected ownership/ancestry
-> exact-head local CI when the repair changes a repo
-> independent review as required
```

### Cloud/deployment work

```text
tavall-skill-orchestrator
-> tavall-memory-plane
-> tavall-git-workflow if source/PR state changes
-> tavall-cloud
-> tavall-ai agent family only when repository/agent implementation work is also in scope
-> service/environment/deployment validation
-> memory writeback if verified/reusable
```

## Registry maintenance

The registry is a bootstrap map, not a frozen list. Runtime discovery wins over stale aliases. When a stable exact skill identity is verified, update the registry in the same integration flow when practical.

Validation should detect:

- a registry exact-current entry whose installed source no longer exists;
- an installed Tavall skill absent from routing/discovery results;
- duplicate exact skill identities;
- circular required dependencies;
- multiple skills claiming the same exclusive operation without a narrower authority rule;
- a required skill whose callable runtime dependency is missing;
- a marketplace plugin whose source path does not exist in the intended integration composition;
- a diff-producing completed task that skipped exact-head Tavall CI;
- a completed consequential task that skipped a required foundation.
