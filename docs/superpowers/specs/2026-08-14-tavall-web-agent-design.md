# Tavall Web Agent Design

## Goal

Add `tavall-agent-web` as the canonical Tavall agent for web product design, A/B visual exploration, visual acceptance, and durable product-scoped design intelligence.

The agent is a reusable behavior/capability package. It does not own a model runtime, browser process, MCP server, filesystem authority, credentials, or infrastructure authority.

## Scope

This first slice intentionally has no internal Web Director, UX Architect, Visual Designer, Frontend Specialist, Visual Critic, or Web QA role decomposition. Tool capability discovery comes first; role boundaries can be introduced later only when real execution evidence justifies them.

The slice includes:

- a discoverable `web` Tavall agent;
- canonical ownership of the web A/B design workflow;
- product-scoped persistent design intelligence;
- typed A/B candidate/comparison/decision contracts;
- durable accepted/rejected design evidence;
- reusable product-intelligence primitives that other Tavall agents can adopt later;
- runtime composition and contract tests.

It does not add or embed Impeccable, Playwright, browser runtimes, MCP servers, or model providers. Those remain authorized external capabilities supplied through Function Catalog and the Tavall AI/Cloud runtime when available.

## Architecture

### Agent boundary

`tavall-agent-web` follows the existing `tavall-agent-*` model. `WebAgentProvider` publishes one `TavallAgent` through `ServiceLoader` with stable id `web` and requires the existing `distributed-execution` runtime module.

The agent advertises coarse repository-read/write and runtime-E2E intent but receives no authority from those declarations. Actual callable tools remain constrained by Function Catalog views and host policy.

The first slice does not hard-code browser or Impeccable function names because Tavall does not yet have a canonical validated Function Catalog surface for those tools. The agent instructions require using authorized web/design tools when they are exposed and prohibit inventing ambient browser/process/filesystem authority.

### Reusable product intelligence

Persistent per-product intelligence is introduced as reusable agent infrastructure under `org.tavall.agent.intelligence`, rather than as a Web-only storage format.

`TavallProductIntelligenceEntry` is an immutable record containing:

- stable entry id;
- product id;
- source agent id;
- category;
- key;
- value;
- rationale;
- disposition (`REFERENCE`, `ACCEPTED`, or `REJECTED`);
- evidence references;
- recorded timestamp.

`TavallProductIntelligenceStore` provides `record` and product/agent scoped `load` operations.

`FileTavallProductIntelligenceStore` is a durable implementation rooted at a path explicitly supplied by the host. Product ids are hashed for directory placement so arbitrary product names never become filesystem paths. Agent and entry ids are validated before use. Writes use one properties file per entry and replace atomically when supported. Loading verifies that persisted product and agent identity match the requested scope.

This store has no ambient workspace discovery. The host chooses the root and therefore retains filesystem authority.

### Web design intelligence

`WebDesignIntelligenceCategory` names the first web-specific knowledge vocabulary:

- product identity;
- audience;
- brand;
- visual principle;
- forbidden pattern;
- typography;
- spacing;
- color;
- component language;
- interaction;
- reference;
- design decision.

`WebDesignIntelligenceService` adapts those categories onto `TavallProductIntelligenceStore`. It can record ordinary design knowledge and load all remembered Web Agent intelligence for one product.

### A/B workflow

The Web Agent owns a typed comparison loop:

1. Inspect existing product intelligence and relevant live/product context.
2. Create a `WebDesignComparison` with at least two distinct `WebDesignCandidate` options for a meaningful visual direction.
3. Render or otherwise gather evidence through authorized tools when available.
4. Select a candidate with `WebDesignDecision`.
5. Persist the selected candidate as `ACCEPTED` and every unselected candidate as `REJECTED`, preserving rationale and evidence.
6. Future work loads those decisions before generating another design.

A comparison rejects duplicate candidate ids, fewer than two candidates, blank product/comparison/brief fields, and decisions that select an unknown candidate.

A/B state is product-scoped. A decision for one product must never appear in another product's design context even when both use the same persistence root.

## Agent instructions

`ROLE.md` defines the operating contract without inventing internal roles:

- load product design intelligence before meaningful design work;
- preserve explicit accepted decisions and avoid repeating rejected directions unless requirements changed;
- prefer real application/live-browser evidence over prose-only aesthetic claims when authorized tools exist;
- use A/B exploration for meaningful visual-direction choices rather than trivial pixel differences;
- persist both the winner and rejected candidates with reasons;
- validate responsive behavior, accessibility, interaction states, and visual consistency before declaring visual work complete;
- use Impeccable and other design MCP/tool surfaces when authorized and useful, but treat them as capabilities rather than architectural dependencies;
- do not infer shell, browser, filesystem, process, credential, deploy, or production authority.

## Runtime composition

`settings.gradle.kts` includes `tavall-agent-web`.

`tavall-ai-runtime` adds the module as `runtimeOnly`, making it part of the same installed agent universe as Builder, Review, Architecture, and the other reusable agents.

No new AI runtime module is introduced.

## Testing

Tests cover:

- provider id, runtime requirement, and absence of internal-role contract;
- ServiceLoader registration through normal project packaging;
- durable intelligence surviving store re-instantiation;
- product isolation;
- invalid path/id inputs;
- Web design category persistence;
- A/B minimum-candidate and uniqueness rules;
- unknown decision rejection;
- accepted and rejected candidate persistence after a decision;
- no `AI`-named Web Agent public types.

Repository-level verification remains `scripts/ci/verify` on Java 25. The connected GitHub surface cannot itself claim that local execution has run.

## Future extensions

Future PRs may add canonical Function Catalog functions for browser automation, screenshots, Impeccable, visual-diff tooling, design references, or component search after their real capabilities are inspected. Internal Web Agent roles should be derived from those validated tool boundaries rather than guessed in advance.

The product-intelligence contract is deliberately generic so other agents can later persist per-product/domain knowledge without introducing independent memory formats.