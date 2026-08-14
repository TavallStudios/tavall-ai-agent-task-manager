---
name: tavall-ai-distributed-execution
description: Route a bounded AI/model call across already-authorized Tavall AI node and web execution surfaces without taking over workload scheduling or Cloud authority.
---

# Tavall AI Distributed Execution

Use this skill when an existing Tavall AI job/session/module needs another AI/model execution and the call may be satisfied by more than one authorized execution surface.

This is **not** the Tavall scheduler. Scheduling answers where durable workload/session ownership lives. Distributed AI execution answers which already-authorized AI runtime/provider should satisfy one AI call.

## Execution flow

1. Preserve the durable job id/version and opaque authority/lease reference supplied by the owning runtime.
2. State the required AI capabilities, such as code, vision, structured reasoning, or Builder-specific critique.
3. Preserve any explicit allowed-surface restriction. Never widen a web-only or node-only request.
4. Use only targets returned by authorized target providers. A provider's target list is the authority-filtered input to routing, not an invitation to discover more machines yourself.
5. Filter unready targets and targets missing required capabilities.
6. Apply explicit surface preferences when present, then stable provider priority/target identity.
7. Fail over only after a retryable provider result and only inside the request's attempt budget.
8. Preserve ordered attempt evidence and the final result/artifact reference.
9. Stop immediately on a non-retryable rejection.
10. Fail closed when no eligible target remains.

## Boundaries

- Tavall Cloud owns DEVELOPMENT eligibility, durable jobs, workspaces, process/network isolation, tool/credential grants, mutation authority, audit and revocation.
- Tavall Scheduler remains generic workload coordination and does not own provider/model semantics.
- Function Catalog owns typed callable functions and MCP projection.
- GitHub CLI and other executables remain Cloud-granted execution capabilities, not Function Catalog wrappers.
- The ChatGPT Web execution runtime is separate from Tavall Cloud's inbound ChatGPT-to-CONTROL MCP adapter.
- Never build a direct worker-to-worker SSH or private agent control plane.

## Builder

Builder is the first domain-agent consumer of this skill. Builder may request distributed vision/reasoning/repair calls while preserving `minecraft-bot-builder` as the authority for BuildSpec, schematic, replay, visual evidence, Studio, FAWE and Mineflayer implementation contracts.
