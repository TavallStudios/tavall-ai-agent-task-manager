# Tavall AI NODE_AGENT Runtime Lane

<!-- tavall-staging:v1 -->
Type: DOMAIN_INTEGRATION
State: ACTIVE
Branch: staging/runtime-node-agent
Parent: PR #8 / `working/tavall-ai-distributed-execution-runtime`
Promotion: MANUAL
ChildMergeTarget: staging/runtime-node-agent

## Purpose

Durable integration and validation lane for the Tavall AI `NODE_AGENT` executable composition.

The shared parent runtime, bootstrap, installed `tavall-agent-*` packages, and reusable AI runtime capability modules remain owned by #8 and its shared children. Agent roles are reusable behavior/function-requirement packages, not process runtimes.

This lane owns Node-Agent-host-specific runtime composition, authorized host adapters, readiness/lifecycle, deployment wiring, and exact DEVELOPMENT acceptance.

## Acceptance focus

- Java 25 local verification and staged distribution checks;
- Node Agent runtime startup/shutdown and installed-agent discovery;
- runtime-module requirement validation;
- bounded distributed/model execution through authorized host adapters;
- Tavall Cloud job/lease/workspace/process authority remaining external and fail-closed;
- restart/recovery, rollback, and untested-path evidence.

This initial commit establishes the lane only. It starts no model process, Cloud job, browser session, deployment, or production mutation.
