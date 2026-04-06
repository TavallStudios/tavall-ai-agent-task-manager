# Git Workflow

Use the first-party MCP git workflow for repository mutation through AgentTaskManager.

## Required Sequence

1. Run `planGitCommit` to render the branch name, subject, body, and grouping recommendation.
2. Run `prepareGitBranch` to create or switch to the deterministic branch.
3. Run `createGitCommit` to stage and commit the current concern.

## Branch Rules

- Default branch template: `domain-system-user-vN`
- Normalize segments to lowercase kebab-case
- Only override branch segments when task metadata explicitly requires it

## Commit Rules

- Subject format: `<Type>: <summary>`
- Body must include `What Changed`, `Why`, and `Verification`
- `Fix` and `Refactor` are only valid when the concern is final for that pass
- Prefer `Added` or `Changed` while a concern is still in progress

## Grouping Rules

- Commit one coherent concern at a time
- Group files only when they still represent one coherent concern
- Use an explicit mixed-domain override when the grouped file set truly must cross concerns
- Keep supporting docs with the same concern only when they directly describe that change
