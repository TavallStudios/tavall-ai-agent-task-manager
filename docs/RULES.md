# RULES

## Core Architecture

- One concern per class.
- No top-level class or interface over 300 lines.
- Use explicit naming. Avoid vague names such as `manager`, `util`, `helper`, `data`, or `map`.
- Prefer positive non-null branches first.
- Edge-case comments must use `// Edge Case: <reason>`.
- Public orchestration, validation, and MCP methods should carry JavaDoc.
- Inner classes are restricted to metadata grouping.

## Dependency Access

- Dependency access interfaces expose actions, not raw getters.
- Compose only same-domain dependency access interfaces.
- Concrete implementations should call inherited action methods directly.
- Do not chain `getXService()` or `getXRegistry()` inside business logic.

## Validation

- ArchUnit guards package boundaries, cycles, forbidden dependency frameworks, MCP boundary cleanliness, and validation or persistence layering.
  Production cycle checks apply to the runtime slices. Shared `model`, `config`, and `loader` infrastructure are excluded from the top-level cycle slice because they exist to support cross-layer contracts and service-loader registration.
- Spoon guards class size, naming, dependency-access shape, comment format, null-branch style, inner classes, mocked test patterns, and inline-call heuristics.
- Cleanup review plus validation must pass before a patch is approved.

## Persistence Roles

- Redis for ephemeral orchestration state.
- Postgres for durable relational truth.
- MongoDB for artifact and chat documents.
- Qdrant for semantic retrieval.
- `cache` for TTL-aware hot and warm caching.
