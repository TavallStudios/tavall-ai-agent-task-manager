---
name: tavall-agent-web
description: Run Tavall web product design and implementation through durable product intelligence, meaningful A/B/C exploration, authorized browser/design evidence, and exact application acceptance.
---

# Tavall Web Agent

Use this skill for substantive Tavall web/UI work. `tavall-agent-web` is the design-workflow home; it is not a browser runtime, a second Function Catalog, or a replacement for the product repository.

## Start from the live capability surface

Treat the authorized Function Catalog as dynamic. Discover the current catalog at execution time rather than depending on the statically injected ChatGPT action list or a remembered tool inventory. Use the stable catalog discovery/describe/invoke entrypoints supplied by the host and inspect typed schemas before invoking newly discovered functions.

Required Web Agent intelligence capabilities are `product_intelligence_read` and `product_intelligence_record`. If they are absent, report the missing platform capability instead of inventing an alternate memory format. Browser, screenshot, visual-diff, component-search, reference, or Impeccable-style capabilities are discovered the same way and are used only when the current authorized catalog exposes them.

For material web-application design, the default design pass is: product intelligence -> inspect the real product -> Impeccable audit/critique/distill when exposed -> Taste/product-design guidance and relevant references -> materially distinct A/B/C candidates -> live browser/screenshots/visual diff -> explicit comparison -> atomic accepted/rejected intelligence -> implementation acceptance. Component/reference search and generated-media tools are used when the feature actually needs those capabilities; do not force a foreign component stack or decorative generated media into a product merely because a tool exists. Omitting an applicable discovered design capability should be an explicit evidence-based choice, not an accidental shortcut.

## Product intelligence first

Before meaningful design work, read durable intelligence for the exact product id and agent id `web`. Use accepted decisions, rejected directions, brand constraints, typography, spacing, color, component language, interaction rules, and prior evidence as real design inputs.

Do not turn the intelligence store into chat history. Record reusable design knowledge only.

## Inspect the real product

Read the repository and existing application before designing. Preserve the product's framework, composition boundaries, tokens, components, routes, account/security behavior, and stronger existing UX. Avoid spawning a parallel frontend stack because a generator made one conveniently.

When a real running application can be inspected, prefer it over static source assumptions.

## Canonical A/B/C exploration

For a material visual-direction decision, compare at least three genuinely distinct candidates against the same brief and acceptance constraints. Do not waste an A/B/C cycle on microscopic spacing variations.

For each candidate, gather the strongest authorized evidence available, including live browser rendering, responsive states, interaction behavior, screenshots, visual diffs, or component/reference evidence. Evaluate hierarchy, clarity, product identity, usability, responsiveness, accessibility, and consistency.

Select one direction with explicit rationale. Persist the complete decision as one atomic `product_intelligence_record` batch containing the accepted candidate and every rejected candidate, including rationale and evidence references. A rejected direction is durable knowledge, not garbage collection.

## Implementation and acceptance

Implement through the normal Tavall repository/workspace/staging path. Web Agent owns design quality and the design decision loop; compose implementation, review, reconciliation, and E2E agents when those concerns are substantive.

Before calling a web change complete, validate the actual application states relevant to it: desktop and mobile/responsive layout, keyboard/accessibility behavior, loading/empty/error states, overflow, interaction feedback, content density, and visual consistency. Compilation or a pretty source diff is not visual acceptance.

## Human control surface

When a product exposes a Web Agent admin surface, use it as a human-facing inspection/control projection of the same authoritative agent and product-intelligence system. Do not create a separate dashboard-owned memory store or a second control plane. The dashboard may show capability readiness, product context, accepted/rejected decisions, evidence, and bounded workflow actions while authority remains with Tavall AI, Function Catalog, Tavall Cloud, and the product runtime.

## Authority

Never infer browser, shell, filesystem, credentials, deployment, production, or infrastructure authority from this skill. Use only the capabilities granted by the current execution and keep mutation bounded to the authorized product/workspace.
