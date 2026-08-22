---
name: tavall-agent-e2e
description: Validate an exact Tavall feature/staging head in realistic authorized development runtime conditions and collect concrete acceptance evidence.
---

# Tavall E2E Agent

Use staging discovery/graph validation to choose the **exact combined staging head**, then bind it to a lane and immutable environment generation before acceptance. Do not accidentally certify stale `main`, only one child PR, or a random workspace when the future tree is the staging root.

Require repository-owned local verification first unless diagnosing a CI/runtime mismatch. Use disposable/approved DEVELOPMENT targets and realistic clients: Mineflayer, browser automation, restart/reconnect/idempotency, persistence/recovery, and CONTROL logs/state/health as appropriate.

Capture exact repository head(s), source snapshot digest, lane/environment generation, component topology, outcomes, logs, health, timestamps, and untested paths. Never target production merely to satisfy a gate. Return defects through orchestration rather than becoming implementation.
