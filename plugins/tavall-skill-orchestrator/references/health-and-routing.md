# Tavall Skill Health and Routing

## Health algorithm

For each Tavall request:

1. Build a candidate route from prompt intent and current repository/runtime context.
2. Add cross-cutting foundations:
   - memory route for every Tavall prompt when available;
   - Git route whenever repository/PR state may change.
3. Discover exact identities and runtime capabilities.
4. Resolve dependencies topologically.
5. Assign `AVAILABLE`, `DEGRADED`, `MISSING`, `SKIPPED_BY_SCOPE`, or `BLOCKED`.
6. Execute only after required policy dependencies have a safe state.
7. Re-run the health gate if the task crosses into a new domain mid-run.

## Memory behavior

The orchestrator routes Tavall prompts through `tavall-memory-plane`; the memory skill decides provider fan-out.

For substantive Tavall work, if the memory skill is known but `memoryContext`/equivalent is not exposed:

```text
memory = DEGRADED
fallback authority = current repository + runtime + tests + logs
forbidden = claiming memory bootstrap succeeded
```

A missing memory provider should not block safe read-only or engineering work when current evidence is sufficient. It must remain visible when the missing context could affect the result.

## Git behavior

If Git or GitHub state may be mutated, `tavall-git-workflow` is a policy dependency. If it cannot be resolved, do not invent a branch/PR topology from memory. Read the canonical repository workflow directly if possible and mark the skill dependency degraded; otherwise block consequential Git policy decisions.

## Route examples

### Java implementation

```text
tavall-skill-orchestrator
-> tavall-memory-plane
-> tavall-git-workflow
-> tavall-java-tools
-> domain-specific runtime skill if required
-> validation
-> memory writeback if verified/reusable
```

### Web/UI work

```text
tavall-skill-orchestrator
-> tavall-memory-plane
-> tavall-git-workflow
-> tavall-web-agent (discover exact identity)
-> impeccable (default helper when available)
-> browser/responsive visual acceptance
-> memory writeback if verified/reusable
```

### Builder Studio work

```text
tavall-skill-orchestrator
-> tavall-memory-plane
-> tavall-git-workflow when repository state changes
-> minecraft-builder
-> rendering-builder-replays only when replay/render verification is in scope
-> impeccable when applicable to visual-quality evaluation
-> real Builder Studio/render/bot evidence
-> memory writeback if verified/reusable
```

### AgentTaskManager / Tavall AI harness work

```text
tavall-skill-orchestrator
-> tavall-memory-plane
-> tavall-git-workflow when repository state changes
-> tavall-ai
-> task runtime / function catalog
```

`tavall-ai` prompt-thread/task-runtime context does not silently replace the Tavall memory-plane foundation.

### Cloud/deployment work

```text
tavall-skill-orchestrator
-> tavall-memory-plane
-> tavall-git-workflow if source/PR state changes
-> tavall-cloud
-> service/environment/deployment validation
-> memory writeback if verified/reusable
```

## Registry maintenance

The registry is a bootstrap map, not a frozen list. Runtime discovery wins over stale aliases. When a stable exact skill identity is verified, update the registry in the same integration flow when practical.

Validation should detect:

- a registry entry whose exact skill no longer exists;
- an installed Tavall skill absent from routing/discovery results;
- circular required dependencies;
- multiple skills claiming the same exclusive operation;
- a required skill whose tool capability is missing;
- a marketplace skill whose source path does not exist;
- a completed consequential task that skipped a required foundation.
