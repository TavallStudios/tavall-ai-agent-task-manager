# Tavall AI Runtime Boundary

Tavall AI has one concrete executable process identity today: `NODE_AGENT`.

```text
Tavall Cloud placement / process authority
                 |
                 v
       tavall-ai-runtime
                 |
          NODE_AGENT process
                 |
       +---------+----------+
       |                    |
 installed role modules   authorized host adapter
       |                    |
       +---------+----------+
                 |
                 v
 Function Catalog agent-runtime / providers
```

## Ownership

`tavall-ai-runtime` owns process identity, startup, installed-role discovery, and fail-closed host-adapter selection.

`tavall-ai-agent-*` modules own reusable role instructions, requested capabilities, and role metadata. A role is never a process runtime by itself.

Function Catalog owns provider-neutral `AIAgentRuntime`, authoritative function views, execution budgets, and concrete model/provider implementations such as the Codex provider. Tavall AI composes those capabilities; it does not fork another tool/model runtime.

Tavall Cloud owns DEVELOPMENT-node placement, durable job state, workspace/process/sandbox authority, capacity, restart policy, and the authorized transport that feeds jobs to an AI Node Agent. The ordinary Tavall Cloud Node Agent must not absorb Tavall AI runtime classes merely to launch AI work.

## Host transport

The runtime discovers exactly one `TavallAINodeAgentHost` implementation through `ServiceLoader` when it is asked to execute normally. Zero or multiple host adapters fail closed. This makes the Cloud/job transport an explicit integration rather than an implicit long-poll loop hidden inside the AI role layer.

`NODE_AGENT --describe` is intentionally transport-free. It validates the process artifact and reports installed roles without claiming CONTROL connectivity.

## Non-runtimes

The following are not Tavall AI process identities:

- scheduler role
- orchestration role
- implementation/review/reconciliation/E2E/architecture/documentation roles
- Function Catalog
- MCP
- local CI
- `tavall-open-harness`

If a future executable gains a distinct lifecycle, authority boundary, deployment policy, or scaling profile, it can become a runtime then. Module names do not get promoted to daemons as a participation trophy.
