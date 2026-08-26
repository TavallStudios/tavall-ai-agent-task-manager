# Tavall Memory Agent Skills

The Tavall AI memory plane ships repository-scoped Agent Skills under `.agents/skills/`. They are checked into the repository so Codex and other Agent Skills-compatible runtimes can discover the same memory workflow instead of relying on per-chat prompting.

## Skill map

| Skill | Use it for |
| --- | --- |
| `tavall-memory-bootstrap` | Start or resume substantive Tavall work with one scoped `memoryContext` hydration and the correct authority order. |
| `tavall-memory-investigation` | Debugging, regression archaeology, prior fixes, Graphify structure/PR impact, Graphiti history, and evidence correlation. |
| `tavall-memory-writeback` | Intentional verified `recordMemory`, supersession, scope/provenance selection, and deterministic temporal facts. |
| `tavall-memory-review` | Independent PR/architecture review with memory-plane evidence, current source, blast radius, authority, concurrency, and transaction checks. |
| `tavall-memory-validation` | Exact-head validation of memory-plane code, deployment, providers, outbox delivery, cache coherence, restart persistence, and ordinary-turn no-growth. |

## Default agent behavior

For substantive Tavall engineering work:

1. `tavall-memory-bootstrap` should normally apply first.
2. Use focused deeper skills only when the task requires them.
3. Do not fan out to every provider on every turn.
4. Do not treat Qdrant similarity as canonical truth.
5. Do not persist ordinary chat/tool traffic as durable memory.
6. Promote only verified, reusable conclusions through `tavall-memory-writeback`.

## Tool ownership

- `memoryContext`: default compiled hydration.
- `searchRelatedContexts`, `searchPriorFixes`: focused semantic investigation.
- `memoryRelated`, `codeImpact`: current Graphify structural evidence.
- `memoryHistory`: Graphiti temporal/history evidence.
- `recordMemory`: explicit durable-memory promotion boundary.
- `recordTemporalFact`: deterministic already-verified temporal relationship write.
- `memoryProviderStats`: provider telemetry and acceptance evidence.

The skills deliberately distinguish **retrieval**, **investigation**, **promotion**, **review**, and **validation**. Collapsing those into one giant memory prompt encourages over-retrieval and accidental writeback, the two failure modes the memory plane is designed to prevent.

## Portability

The skill folders follow the Agent Skills `SKILL.md` convention and can be reused by compatible agent surfaces. Repository-local installation is the canonical source; packaging them into a Tavall ChatGPT/Codex plugin can reuse these directories without maintaining a second instruction set.
