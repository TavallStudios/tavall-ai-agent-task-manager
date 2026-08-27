# AgentTaskManager Deprecation

> **Status:** Deprecated architecture; migration source only.

The historical AgentTaskManager runtime is no longer the active Tavall AI architecture.

## Why

AgentTaskManager accumulated orchestration, persistence, MCP, repository, validation, memory, computer-use, UI, and Codex execution responsibilities behind a large shared `tavall-ai-core`. The newer architecture has clearer owners:

- Function Catalog owns typed AI functions, restricted catalog views, provider-neutral agent runtime primitives, MCP projection, and model/provider adapters.
- Tavall Cloud owns DEVELOPMENT-only placement, durable job/capability authority, machine capacity, workspace/process/sandbox infrastructure, and local CI execution authority.
- `tavall-ai-agent-ROLE` modules own small role-specific instructions and requested function capabilities.
- Tavall AI Node Agent implementations compose those pieces on explicitly AI-capable development nodes.
- Codex supplies the session/subagent reasoning runtime; Tavall orchestration composes specialized roles rather than recreating a model runtime.

## Build status

The Gradle root no longer includes the historical AgentTaskManager modules. Existing directories remain in the repository temporarily as migration/reference inputs but are not part of the active build.

No new features should be added to the legacy modules.

## Salvage policy

Do not migrate code merely because it exists. Move or reimplement a legacy capability only when the replacement architecture has a concrete gap and the existing implementation remains the best production-quality source.

Likely salvage categories include deterministic validation rules, useful typed contracts, or narrowly reusable computer-use adapters. Legacy persistence/orchestration stacks are not automatically preserved.

## Repository transition

The active project identity is `tavall-ai`. The GitHub repository is still named `tavall-ai-agent-task-manager` during this source transition. Repository rename or replacement can happen after the active role/runtime boundaries are validated.
