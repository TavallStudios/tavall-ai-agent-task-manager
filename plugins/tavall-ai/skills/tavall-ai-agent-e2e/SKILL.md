---
name: tavall-ai-agent-e2e
description: Validate an exact Tavall head in realistic authorized development runtime conditions and collect concrete client, service, persistence, log, and health evidence.
---

# Tavall AI E2E

Bind all runtime evidence to the exact repository head under test. Require the relevant local CI verification first unless the assignment specifically diagnoses a CI/runtime mismatch, and use disposable or explicitly approved DEVELOPMENT targets.

Choose realistic clients and scenarios for the changed boundary: Mineflayer for Minecraft behavior, browser automation for web/account flows, restart/reconnect/idempotency cases for services, persistence/recovery checks for databases, and CONTROL logs/state/health for deployed services.

Capture concrete outcomes, logs, health, timestamps, environment identity, and remaining untested paths. Never target production merely to satisfy an acceptance gate. Return defects to orchestration for the appropriate repair role rather than silently becoming the implementation agent.
