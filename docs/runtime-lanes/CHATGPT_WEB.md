# Tavall AI CHATGPT_WEB Runtime Lane

<!-- tavall-staging:v1 -->
Type: DOMAIN_INTEGRATION
State: ACTIVE
Branch: staging/runtime-chatgpt-web
Parent: PR #8 / `working/tavall-ai-distributed-execution-runtime`
Promotion: MANUAL
ChildMergeTarget: staging/runtime-chatgpt-web

## Purpose

Durable integration and validation lane for the Tavall AI `CHATGPT_WEB` executable composition.

The shared parent runtime, bootstrap, installed `tavall-agent-*` packages, and reusable AI runtime capability modules remain owned by #8 and its shared children. Agent roles and Function Catalog schemas are not separate process runtimes.

This lane owns ChatGPT-Web-host-specific runtime composition, dispatch/session/plugin adapters, readiness/lifecycle, deployment wiring, and exact DEVELOPMENT acceptance.

## Acceptance focus

- Java 25 local verification and staged distribution checks;
- ChatGPT Web runtime startup/shutdown and installed-agent discovery;
- runtime-module requirement validation;
- typed Function Catalog dispatch/session surfaces through the authorized adapter boundary;
- Tavall Cloud operational authority remaining external and fail-closed;
- restart/recovery, rollback, and untested-path evidence.

This initial commit establishes the lane only. It starts no model process, browser session, Cloud job, deployment, or production mutation.
