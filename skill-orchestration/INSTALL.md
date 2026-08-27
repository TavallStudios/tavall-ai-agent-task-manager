# Installation and Acceptance

## Installation model

Install `tavall-skill-orchestrator` and `tavall-git-workflow` as sibling Tavall Coding skills/plugins. Keep domain skills separately installed/discoverable. When the Tavall AI plugin is installed, its marketplace source is `./plugins/tavall-ai` and its top-level operating skill is `tavall-ai`.

Make `tavall-skill-orchestrator` the Tavall entry-point instruction rather than copying its routing logic into every specialist:

```text
For Tavall-related work, invoke tavall-skill-orchestrator first. It owns broad skill discovery, foundation checks, Tavall AI agent-family routing, and completion-gate selection. Domain skills own implementation and must not bypass required Git, memory, or exact-head completion policy.
```

## Memory dependency

`tavall-memory-plane` remains an external foundation. This bundle does not copy the memory skill into itself. For substantive Tavall work, acceptance requires `memoryContext` or the current equivalent when the memory skill/runtime exposes it. If unavailable, report memory as degraded and continue only from current authoritative evidence where safe.

## Tavall AI agent family

When the current Tavall AI plugin is installed, acceptance should resolve the exact top-level `tavall-ai` skill plus the specialized `tavall-agent-*` skills exposed by that plugin. `agent-task-manager` must remain a distinct narrower identity rather than colliding with the `tavall-ai` entry point.

## Exact-head completion

`tavall-local-ci` is a conditional completion skill. A diff-producing Tavall engineering run must validate its final immutable HEAD through that skill when installed, or through the current typed Tavall Cloud LOCAL_CI equivalent when the skill is degraded. Do not treat ordinary local tests or a successful job for another SHA as current exact-head CI evidence.

## Acceptance checks

1. `tavall-skill-orchestrator` is discoverable by exact name.
2. `tavall-git-workflow` is discoverable by exact name.
3. Marketplace `tavall-ai` resolves to `./plugins/tavall-ai` in the ChatGPT/Tavall AI integration composition.
4. `tavall-ai` resolves as the top-level Tavall AI operating entry point.
5. `agent-task-manager` resolves as a distinct harness/task-runtime specialist with no duplicate `tavall-ai` identity.
6. `tavall-agent-orchestration` and the installed acceptance-unit specialists resolve from the Tavall AI plugin.
7. `tavall-agent-scheduler` is not selected merely to obtain another same-session specialist.
8. `tavall-ai-distributed-execution` remains model-call routing, not durable workload scheduling.
9. `tavall-memory-plane` and its memory capability resolve when installed; otherwise the route visibly degrades.
10. `minecraft-builder`, replay rendering, Web Agent, Impeccable, Java Tools, and Tavall Cloud resolve through their current installed/catalog surfaces without invented identities.
11. A diff-producing engineering route selects `tavall-local-ci` or the typed Tavall Cloud exact-head equivalent before completion.
12. A missing required foundation produces `DEGRADED`, `MISSING`, or `BLOCKED`, never silent omission.
13. The Git skill reads the current canonical `GIT_WORKFLOW.md` and stricter repository-local rules before consequential PR topology changes.
14. Registry/bundle/agent YAML and marketplace JSON parse cleanly.
15. No circular required dependency exists in the initial graph.

## Runtime acceptance

Run at least one Tavall engineering prompt from the installed ChatGPT surface and inspect the route. For a repository mutation, the expected shape is memory when available -> Git policy -> Tavall AI -> Tavall agent orchestration -> narrow specialist(s) -> exact-head LOCAL_CI -> review/E2E as required -> conditional memory writeback. Record only evidence actually observed.
