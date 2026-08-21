# Tavall Web Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `tavall-agent-web` with canonical A/B web-design workflow and durable per-product design intelligence while preserving Tavall AI's agent/runtime authority boundaries.

**Architecture:** Reuse `tavall-ai-bootstrap` for generic agent intelligence contracts and a host-rooted file persistence implementation, then build Web-specific typed design/A-B contracts in a new `tavall-agent-web` module. The runtime discovers the module through ServiceLoader exactly like existing agents; external browser/Impeccable/MCP execution remains outside the module.

**Tech Stack:** Java 25, Gradle Kotlin DSL, JUnit 5, Java NIO, ServiceLoader.

## Global Constraints

- No internal Web Agent role decomposition in this slice.
- The public module name is `tavall-agent-web`; stable agent id is `web`.
- Persistent intelligence is product-scoped and reusable by future agents.
- The persistence root is always explicitly supplied by the host; no ambient workspace/filesystem discovery.
- Browser, Impeccable, MCP, model, process, credential, deploy, and production authority remain external.
- Canonical repository verification is `scripts/ci/verify` on Java 25.

---

### Task 1: Reusable product-intelligence persistence

**Files:**
- Create: `tavall-ai-bootstrap/src/main/java/org/tavall/agent/intelligence/TavallProductIntelligenceDisposition.java`
- Create: `tavall-ai-bootstrap/src/main/java/org/tavall/agent/intelligence/TavallProductIntelligenceEntry.java`
- Create: `tavall-ai-bootstrap/src/main/java/org/tavall/agent/intelligence/TavallProductIntelligenceStore.java`
- Create: `tavall-ai-bootstrap/src/main/java/org/tavall/agent/intelligence/FileTavallProductIntelligenceStore.java`
- Create: `tavall-ai-bootstrap/src/test/java/org/tavall/agent/intelligence/FileTavallProductIntelligenceStoreTest.java`

**Interfaces:**
- Produces: `TavallProductIntelligenceStore.record(TavallProductIntelligenceEntry)` and `load(String productId, String agentId)`.
- Produces: durable `FileTavallProductIntelligenceStore(Path root)` with product isolation and host-controlled root.

- [ ] **Step 1: Write failing persistence and isolation tests**

Create tests that record an entry, instantiate a second store over the same temporary root, verify the entry survives, and verify another `productId` returns no entry. Add validation tests for unsafe blank/invalid agent and entry identifiers.

- [ ] **Step 2: Run the focused bootstrap tests and confirm they fail**

Run:

```bash
./gradlew :tavall-ai-bootstrap:test --tests '*FileTavallProductIntelligenceStoreTest'
```

Expected before implementation: compilation failure because the intelligence types do not exist.

- [ ] **Step 3: Implement the minimal generic contract and file store**

Use immutable copied collections. Persist one `.properties` file per entry. Hash the product id with SHA-256 for the product directory, validate agent/entry ids against `[A-Za-z0-9._-]+`, normalize the supplied root, write via a temporary sibling and atomic move when supported, and verify stored product/agent identity during reads.

- [ ] **Step 4: Run focused bootstrap tests**

```bash
./gradlew :tavall-ai-bootstrap:test --tests '*FileTavallProductIntelligenceStoreTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tavall-ai-bootstrap/src/main/java/org/tavall/agent/intelligence tavall-ai-bootstrap/src/test/java/org/tavall/agent/intelligence
git commit -m 'feat: add product intelligence persistence'
```

### Task 2: Typed Web design and A/B contracts

**Files:**
- Create: `tavall-agent-web/build.gradle.kts`
- Create: `tavall-agent-web/src/main/java/org/tavall/agent/web/WebDesignIntelligenceCategory.java`
- Create: `tavall-agent-web/src/main/java/org/tavall/agent/web/WebDesignCandidate.java`
- Create: `tavall-agent-web/src/main/java/org/tavall/agent/web/WebDesignComparison.java`
- Create: `tavall-agent-web/src/main/java/org/tavall/agent/web/WebDesignDecision.java`
- Create: `tavall-agent-web/src/main/java/org/tavall/agent/web/WebDesignIntelligenceService.java`
- Test: `tavall-agent-web/src/test/java/org/tavall/agent/web/WebDesignIntelligenceServiceTest.java`

**Interfaces:**
- Consumes: `TavallProductIntelligenceStore` from Task 1.
- Produces: Web-specific design-memory recording/loading and A/B decision persistence.

- [ ] **Step 1: Write failing A/B and design-memory tests**

Cover at least two distinct candidates, duplicate candidate rejection, unknown selected candidate rejection, normal design knowledge persistence, accepted winner persistence, rejected alternative persistence, and product isolation.

- [ ] **Step 2: Run Web module tests and confirm failure**

```bash
./gradlew :tavall-agent-web:test --tests '*WebDesignIntelligenceServiceTest'
```

Expected before implementation: module/type compilation failure.

- [ ] **Step 3: Implement minimal Web design contracts**

`WebDesignComparison` validates nonblank ids/brief/product and at least two unique candidates. `WebDesignDecision` carries selected candidate id and rationale. `WebDesignIntelligenceService.recordDecision` writes one `DESIGN_DECISION` entry per candidate with `ACCEPTED` for the winner and `REJECTED` for the rest, preserving candidate rationale/evidence and decision rationale.

- [ ] **Step 4: Run Web design tests**

```bash
./gradlew :tavall-agent-web:test --tests '*WebDesignIntelligenceServiceTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tavall-agent-web
git commit -m 'feat: add web design intelligence workflow'
```

### Task 3: Web Agent provider and instructions

**Files:**
- Create: `tavall-agent-web/src/main/java/org/tavall/agent/web/WebAgentProvider.java`
- Create: `tavall-agent-web/src/main/resources/org/tavall/agent/web/ROLE.md`
- Create: `tavall-agent-web/src/main/resources/META-INF/services/org.tavall.agent.TavallAgentProvider`
- Create: `tavall-agent-web/src/test/java/org/tavall/agent/web/TavallWebAgentTest.java`

**Interfaces:**
- Consumes: existing `TavallAgent`/`TavallAgentProvider` bootstrap contracts.
- Produces: ServiceLoader-discoverable agent id `web` requiring runtime module `distributed-execution`.

- [ ] **Step 1: Write failing provider contract test**

Assert id `web`, required runtime module `distributed-execution`, intended coarse capabilities, no internal Web role enum/contract, and no public Web type with `AI` in its name.

- [ ] **Step 2: Run provider test and confirm failure**

```bash
./gradlew :tavall-agent-web:test --tests '*TavallWebAgentTest'
```

Expected: FAIL until provider/resources exist.

- [ ] **Step 3: Implement provider, ServiceLoader descriptor, and ROLE.md**

ROLE.md must require loading product intelligence before meaningful design work, meaningful A/B exploration, durable accepted/rejected rationale, live/rendered evidence when authorized, responsive/accessibility/state validation, authorized use of Impeccable/design MCPs, and strict non-authority for browser/process/filesystem/credentials/deploy/production.

Do not introduce internal named roles or hard-code unvalidated browser/Impeccable Function Catalog names.

- [ ] **Step 4: Run Web module tests**

```bash
./gradlew :tavall-agent-web:test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tavall-agent-web
git commit -m 'feat: add Tavall Web Agent provider'
```

### Task 4: Runtime composition

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `tavall-ai-runtime/build.gradle.kts`
- Modify: any runtime/bootstrap registry test whose exact installed-agent set is asserted.

**Interfaces:**
- Consumes: `tavall-agent-web` ServiceLoader provider.
- Produces: Web Agent in the standard Tavall AI installed agent universe.

- [ ] **Step 1: Add/adjust a failing runtime discovery assertion**

Where the existing runtime tests assert installed agent ids/count, add `web` to the expected set.

- [ ] **Step 2: Run runtime-focused tests and confirm failure before composition**

```bash
./gradlew :tavall-ai-runtime:test
```

Expected before module wiring: discovery assertion does not include `web`.

- [ ] **Step 3: Wire the module**

Add `"tavall-agent-web"` to `settings.gradle.kts` and `runtimeOnly(project(":tavall-agent-web"))` beside the other agent modules in `tavall-ai-runtime/build.gradle.kts`.

- [ ] **Step 4: Run runtime tests**

```bash
./gradlew :tavall-ai-runtime:test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts tavall-ai-runtime
git commit -m 'feat: install Tavall Web Agent in runtime'
```

### Task 5: Exact-head verification and PR evidence

**Files:**
- Verify only; update PR body with evidence after execution.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: one stacked PR against `working/tavall-ai-distributed-execution-runtime` with truthful validation state.

- [ ] **Step 1: Run full repository verifier when an authorized Java 25 local worker is available**

```bash
scripts/ci/verify
```

Expected: `clean check stageDistribution` succeeds.

- [ ] **Step 2: If the connected execution surface cannot run Java 25, do not substitute GitHub Actions or invent evidence**

Record `SOURCE_REVIEWED; JAVA25_LOCAL_CI_PENDING` and name the exact head requiring validation.

- [ ] **Step 3: Review branch diff for authority and scope regressions**

Confirm there is no embedded model/browser/MCP/process runtime, no internal Web role split, and no path derived directly from arbitrary product id.

- [ ] **Step 4: Open the stacked PR**

Base: `working/tavall-ai-distributed-execution-runtime`.

Title: `Agents: add Tavall Web design agent`.

PR body must describe A/B ownership, persistent per-product intelligence, generic future-agent reuse, current tool-boundary deferral, and exact validation truth.