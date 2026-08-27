# Installation and Acceptance

## Installation model

Install `tavall-skill-orchestrator` and `tavall-git-workflow` as sibling Tavall Coding skills/plugins. Keep existing domain skills separately installed/discoverable.

Make `tavall-skill-orchestrator` the Tavall entry-point instruction rather than copying its routing logic into every specialist:

```text
For Tavall-related work, invoke tavall-skill-orchestrator first. It owns broad skill discovery, foundation checks, and routing. Domain skills own their implementation domains and must not bypass required Git or memory foundations.
```

## Memory dependency

`tavall-memory-plane` remains an external foundation. This bundle does not copy the historical memory skill into itself.

For every Tavall prompt, the orchestrator should route through the memory skill when it is available. The memory skill decides whether provider work is required. For substantive Tavall work, acceptance requires `memoryContext` or the current equivalent to be exposed so BOOTSTRAP can occur once early.

If the memory skill/tooling is unavailable, the orchestrator must report the dependency as degraded and continue only from current authoritative evidence where safe.

## Acceptance checks

1. `tavall-skill-orchestrator` is discoverable by exact name.
2. `tavall-git-workflow` is discoverable by exact name.
3. The orchestrator can resolve `tavall-ai` from its current live skill source.
4. The orchestrator discovers `tavall-memory-plane` and its memory capability when installed.
5. Known `minecraft-builder` and `rendering-builder-replays` identities resolve when their plugin surface is installed.
6. Web Agent, Impeccable, Java Tools, and Tavall Cloud aliases resolve through runtime/catalog discovery without inventing an exact identity.
7. A missing required foundation produces `DEGRADED`, `MISSING`, or `BLOCKED`, never silent omission.
8. The Git skill reads the current canonical `GIT_WORKFLOW.md` and repository-local stricter rules before consequential PR topology changes.
9. Registry YAML and marketplace JSON parse cleanly.
10. No circular required dependency exists in the initial graph.

## Recommended follow-up

After install, run one Tavall engineering prompt with memory tools exposed and inspect the route. It should show memory bootstrap, Git routing when repository state is involved, the relevant domain skill, validation, and conditional memory writeback.
