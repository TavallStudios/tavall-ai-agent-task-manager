# Tavall AI Project Context Attachments

> **Status:** Provider-neutral attachment contract implemented on the Tavall AI runtime stack; live ChatGPT Project gateway integration remains host-owned acceptance work.

## Purpose

Tavall should be the shared context layer between an AI worker and the project context that worker needs.

The target flow is:

```text
Codex / future model provider
        ^
        |
tavall-ai-runtime-model-execution
        ^
        |
normalized TavallAIProjectContextBundle
        ^
        |
tavall-ai-runtime-project-context
        ^
        |
ChatGPTProjectContextSource
        ^
        |
host-supplied authorized ChatGPTProjectContextGateway
        ^
        |
ChatGPT Project
  chats / memories / files / instructions / project metadata
```

The same context contract may later serve other project/context systems without teaching Codex or the model-execution engine about their native schemas.

## Ownership boundary

### Tavall AI

Tavall AI owns:

- provider-neutral context kinds and items;
- bounded project-context requests;
- source-adapter registration and resolution;
- normalized bundles with project/source provenance;
- context attachment to provider-neutral model execution;
- provider projection of the normalized bundle.

### Tavall Cloud / authorized runtime host

The host owns:

- permission to access a ChatGPT Project or any other external context source;
- credentials/tokens/session authority;
- the concrete `ChatGPTProjectContextGateway` implementation;
- audit/lease/policy around which project may be attached to which execution;
- runtime placement and process authority.

Tavall AI does not mint access, scrape ChatGPT, or assume an undocumented OpenAI Projects API.

### ChatGPT Project adapter

`ChatGPTProjectContextSource` is a source adapter, not a model-provider feature. It receives a bounded request and delegates retrieval to a host-supplied gateway.

The adapter independently:

1. verifies the returned project id matches the requested project;
2. filters to requested context kinds;
3. deduplicates entries by stable item id while preserving source relevance order;
4. enforces the Tavall-side item budget;
5. enforces the Tavall-side character budget, even if the external gateway over-returns;
6. preserves the source snapshot/version and per-item provenance metadata.

## Context kinds

The initial normalized kinds are:

- `CHAT`
- `MEMORY`
- `FILE`
- `INSTRUCTION`
- `PROJECT_METADATA`

Only `INSTRUCTION` items are projected to Codex as project instructions. Other context kinds are explicitly framed as evidence/data and cannot widen runtime, tool, workspace, deployment, or infrastructure authority.

## Model execution

`TavallAIModelExecutionRequest` now carries an optional `TavallAIProjectContextBundle`.

The existing four-argument request constructor and three-argument execution-engine call remain valid and attach an empty context bundle. This keeps existing providers/callers source-compatible while the attachment path rolls out.

A caller that has already resolved an authorized project slice can use the context-aware execution-engine overload.

## Codex projection

`CodexModelProvider` remains provider-specific only at the final projection step. It does not know how ChatGPT Projects are fetched.

It receives the normalized Tavall bundle and adds a bounded context section to the delegated prompt before the task. Project/source/version and item ids are retained for provenance.

This gives Codex project context without creating a direct ChatGPT-to-Codex dependency.

## Security rules

- Project attachment never grants workspace or process authority.
- Attached context never grants Function Catalog capabilities.
- Non-`INSTRUCTION` context is data, not executable authority.
- External project access is host-authorized and host-audited.
- Returned context is re-bounded by Tavall even when the source gateway claims to enforce the same limits.
- Wrong-project gateway responses fail closed.

## Acceptance

Source-level acceptance in this PR should cover:

- ChatGPT project identity mismatch rejection;
- context-kind filtering;
- stable-id deduplication;
- max-item enforcement;
- max-character enforcement;
- provenance/version preservation;
- existing model-execution API compatibility;
- normalized bundle projection into Codex without ChatGPT-specific retrieval coupling.

Operational acceptance still requires an authorized DEVELOPMENT host to provide a real ChatGPT Project gateway and a real Codex execution lease, then prove that relevant chats/memories/files/instructions reach Codex through Tavall at the exact PR head.

## Non-goals

This slice does not:

- claim an official or undocumented ChatGPT Projects API exists;
- scrape ChatGPT web state;
- dump an account's full conversation history into a model;
- give Codex ambient ChatGPT credentials;
- move context-access policy out of Tavall Cloud/runtime-host authority;
- make ChatGPT the canonical Tavall context schema.
