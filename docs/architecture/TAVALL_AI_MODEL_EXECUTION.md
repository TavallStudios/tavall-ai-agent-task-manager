# Tavall AI Model Execution Runtime

> **Status:** Active ownership boundary implemented in the Tavall AI runtime stack.

## Purpose

Actual model execution belongs to Tavall AI, not Function Catalog and not a reusable Tavall agent package.

The active layering is:

```text
TavallAgent
  non-AI behavior/instructions/function requests
        |
        v
TavallAIModelExecutionDefinition
  TavallAgent + selected model provider id
        |
        v
tavall-ai-runtime-model-execution
  budgets / authoritative function view / provider invocation
        |
        +--------------------+
        |                    |
        v                    v
tavall-ai-runtime-codex   future model providers
        |
        v
host-supplied workspace/process authority
```

## Modules

### `tavall-ai-runtime-model-execution`

Owns provider-neutral single-model execution:

- `TavallAIModelExecutionDefinition`
- `TavallAIModelJob`
- `TavallAIModelExecutionBudget`
- `TavallAIModelExecutionRequest`
- `TavallAIModelExecutionResult`
- `TavallAIModelExecutionStatus`
- `TavallAIModelProvider`
- `TavallAIModelFunctionViewResolver`
- `TavallAIModelExecutionEngine`

The execution definition wraps the canonical non-AI `TavallAgent`; it does not create a second agent-definition model.

The engine:

1. validates delegation budget;
2. resolves the selected model provider;
3. requests an authoritative Function Catalog policy view;
4. rejects a view backed by another catalog;
5. intersects that policy view with the agent's requested function names;
6. applies the tool-call invocation budget;
7. executes the provider on a virtual thread with a bounded timeout;
8. uses observed Function Catalog invocation count rather than trusting provider-reported tool counts;
9. revokes the effective view after completion/failure/timeout;
10. returns structured failure codes instead of silently widening authority.

Function Catalog remains authoritative for catalog definitions, views, invocation, schema, policy/audit hooks, and MCP projection. Tavall AI merely consumes its `ai-core` contract.

### `tavall-ai-runtime-codex`

Owns the actual Codex model/process adapter:

- `CodexModelProvider`
- `CodexModelProviderConfiguration`
- `CodexCommandBuilder`
- `CodexSandboxMode`
- `CodexWorkspaceResolver`
- `CodexProcessIsolationSupervisor`

The provider keeps the fixed non-interactive Codex CLI shape, environment allowlist, bounded captured output, temporary run-directory cleanup, explicit Git-root validation, and structured provider result.

## Process authority

Moving the Codex provider into Tavall AI does **not** move process authority into the provider.

The host must supply:

- an already-authorized workspace through `CodexWorkspaceResolver`;
- a `CodexProcessIsolationSupervisor` that owns process identity/group/cgroup, environment isolation, cancellation, lifetime, and bounded stdout/stderr capture.

Tavall Cloud remains the normal DEVELOPMENT host authority. The provider never receives ambient permission to choose arbitrary workspaces or spawn unsupervised model processes.

## Relationship to distributed execution

These are separate runtime layers:

```text
tavall-ai-runtime-distributed-execution
  -> which already-authorized execution surface/target should receive a bounded AI call?

tavall-ai-runtime-model-execution
  -> how is one selected provider invoked with the agent's scoped Function Catalog view and budget?
```

Distributed execution owns cross-target routing/failover. Model execution owns the single-provider invocation contract. Their request/result types intentionally use different names.

## Function Catalog migration

The former Function Catalog modules `agent-runtime` and `codex-agent-provider` are replaced by the Tavall AI modules above. Function Catalog should remove those runtime/provider modules after this Tavall AI replacement is integrated/available to consumers.

Function Catalog remains a dependency only through `org.tavall:ai-core` for typed function/catalog-view integration.

## Verification

The repository-local source of truth remains:

```text
scripts/ci/verify
```

Required exact-head acceptance includes:

- model execution policy intersection;
- wrong-catalog rejection;
- observed tool-call accounting;
- tool/delegation budgets;
- provider timeout/missing-provider failures;
- Codex fixed command shape and sandbox modes;
- explicit inherited-environment allowlist;
- host-owned absolute workspace/input supervisor contract;
- later DEVELOPMENT runtime acceptance with a real host-supplied Codex process supervisor.

Source implementation alone is not evidence that the live Codex/Cloud boundary passed.
