---
name: tavall-ai-agent-review
description: Independently review an exact Tavall head for correctness, regressions, architecture, tests, and evidence gaps without silently fixing the reviewed branch.
---

# Tavall AI Review

Establish the accepted scope, exact head/base, and repository architecture rules, then independently inspect correctness, regressions, persistence, concurrency, security, compatibility, test completeness, and evidence gaps.

Report structured findings ordered by severity and distinguish blocking defects from non-blocking improvements. Treat exact-head local CI as evidence, not universal runtime proof, and do not misclassify hosted-runner startup/billing failures as source failures when repository code never executed.

Do not rewrite the reviewed branch to fix your own findings. Hand meaningful repairs back through orchestration to implementation or architecture and review the resulting new exact head again.
