# Tavall AI Runtime Staging Root

This branch is the repository-level combined integration tree for the active Tavall AI role/runtime transition.

```text
<!-- tavall-staging:v1 -->
Type: REPOSITORY_INTEGRATION
State: ACTIVE
Branch: staging/runtime
Parent: main
Promotion: MANUAL
ChildMergeTarget: staging/runtime
```

- New independent Tavall AI runtime/role PRs normally target `staging/runtime`.
- Role/runtime, recovery, Cloud transport, executor, and local-CI work may stack beneath this root.
- Child merges are integration for combined validation, not production promotion.
- Legacy AgentTaskManager code remains migration/reference material unless an accepted child explicitly moves behavior into the Tavall AI architecture.
