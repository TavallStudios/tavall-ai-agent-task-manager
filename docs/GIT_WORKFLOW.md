# Git Workflow

AgentTaskManager expects repository mutation to use the first-party git workflow MCP tools instead of raw shell git commands.

Do not use downstream generic git mutation tools such as `git_commit`, `git_add`, `git_checkout`, `git_create_branch`, or `git_reset` through the AgentTaskManager surface. The central server should expose only the first-party mutation workflow for deterministic branch and commit behavior.

Required workflow:

1. Run `runHarnessToolBundle(repo-context)` before editing so repository context, git status, filesystem state, and search results are brokered through the central MCP.
2. Use `planGitCommit` to render the branch name, verbose commit subject, and grouping recommendation for the current concern.
3. Use `prepareGitBranch` to create or switch to the deterministic branch for the concern.
4. Use `createGitCommit` to stage and commit the concern through the first-party workflow.

Default branch template:

- `domain-system-user-vN`
- lowercase kebab-case normalization
- override fields are allowed, but defaults should stay deterministic

Default commit template:

- subject: `<Type>: <summary>`
- body must include `What Changed`, `Why`, and `Verification`
- `Fix` and `Refactor` require `Final Change: yes`

Grouping rules:

- commit one concern at a time
- grouped commits are allowed only when the file set still represents one coherent concern or an explicit mixed-domain override exists
- supporting root docs such as `README.md` and `TOOLS.md` can accompany a single primary concern, but mixed source domains should be split before committing
