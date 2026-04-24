package org.tavall.ai.app.orchestration;

import java.util.Set;

public record CodexRunResult(
    String stdout,
    String stderr,
    int exitCode,
    int effectiveExitCode,
    String finalMessage,
    Set<String> observedToolCalls,
    String diffText,
    boolean diffPresent,
    GitWorktreeManager.GitHeadState finalGitState,
    ContextualToolPolicyService.ToolPolicyAudit toolPolicyAudit
) {
}

