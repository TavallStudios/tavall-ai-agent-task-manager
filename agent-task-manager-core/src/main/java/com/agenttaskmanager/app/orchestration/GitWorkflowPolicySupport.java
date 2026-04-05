package com.agenttaskmanager.app.orchestration;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GitWorkflowPolicySupport {

  private static final Set<String> GENERIC_GIT_MUTATION_CALLS = Set.of(
      "gitadd",
      "gitcheckout",
      "gitcommit",
      "gitcreatebranch",
      "gitreset"
  );

  public Set<String> filterRequiredCalls(
      ContextualToolPolicyService.ToolPolicyDecision decision,
      String diffText,
      ContextualToolPolicyService.GitWorkflowEvidence gitWorkflowEvidence
  ) {
    Set<String> requiredCalls = new LinkedHashSet<>(decision.requiredCalls());
    if (!gitWorkflowRequired(decision, diffText, gitWorkflowEvidence)) {
      requiredCalls.remove("plangitcommit");
      requiredCalls.remove("preparegitbranch");
      requiredCalls.remove("creategitcommit");
    }
    return requiredCalls;
  }

  public Set<String> validate(
      ContextualToolPolicyService.ToolPolicyDecision decision,
      String diffText,
      ContextualToolPolicyService.GitWorkflowEvidence gitWorkflowEvidence,
      Set<String> normalizedObserved
  ) {
    Set<String> violations = new LinkedHashSet<>();
    if (!gitWorkflowRequired(decision, diffText, gitWorkflowEvidence)) {
      return violations;
    }
    for (String observed : normalizedObserved) {
      if (GENERIC_GIT_MUTATION_CALLS.contains(observed)) {
        violations.add(
            "Use planGitCommit, prepareGitBranch, and createGitCommit instead of downstream git mutation tools: "
                + observed
        );
      }
    }
    if (!commitCreated(gitWorkflowEvidence)) {
      violations.add("Git workflow must create a new commit for repository changes.");
    }
    if (commitCount(gitWorkflowEvidence) > 1) {
      violations.add("Git workflow must produce exactly one new commit per prompt/message.");
    }
    if (gitWorkflowEvidence.branchName() == null || gitWorkflowEvidence.branchName().isBlank()) {
      violations.add("Git workflow must end on a named branch.");
    } else if (!gitWorkflowEvidence.branchName().matches("^[a-z0-9][a-z0-9-]*-[a-z0-9][a-z0-9-]*-[a-z0-9][a-z0-9-]*-v\\d+$")) {
      violations.add("Git branch must follow the domain-system-user-vN pattern.");
    }
    if (gitWorkflowEvidence.headCommitHash() == null || gitWorkflowEvidence.headCommitHash().isBlank()) {
      violations.add("Git workflow must produce a commit for repository changes.");
    }
    if (gitWorkflowEvidence.headSubject() == null || !gitWorkflowEvidence.headSubject().matches("^(Added|Changed|Fix|Refactor|Removed): .+")) {
      violations.add("Git commit subject must use the required change-type prefix.");
    }
    String body = gitWorkflowEvidence.headBody() == null ? "" : gitWorkflowEvidence.headBody();
    if (!body.contains("What Changed:") || !body.contains("Why:") || !body.contains("Verification:")) {
      violations.add("Git commit body must include What Changed, Why, and Verification sections.");
    }
    String subject = gitWorkflowEvidence.headSubject() == null ? "" : gitWorkflowEvidence.headSubject();
    if ((subject.startsWith("Fix:") || subject.startsWith("Refactor:"))
        && !body.contains("Final Change: yes")) {
      violations.add("Fix and Refactor commits require Final Change: yes in the verbose body.");
    }
    return violations;
  }

  public boolean gitWorkflowRequired(
      ContextualToolPolicyService.ToolPolicyDecision decision,
      String diffText,
      ContextualToolPolicyService.GitWorkflowEvidence gitWorkflowEvidence
  ) {
    return decision.repoBackedWriteRun()
        && !decision.readOnlyMode()
        && gitScopeEnabled(decision.gitEnforcementScope())
        && gitWorkflowEvidence != null
        && gitWorkflowEvidence.gitRepository()
        && diffPresent(diffText);
  }

  public boolean diffPresent(String diffText) {
    return diffText != null && !diffText.isBlank();
  }

  public String enforcementReason(
      ContextualToolPolicyService.ToolPolicyDecision decision,
      String diffText,
      ContextualToolPolicyService.GitWorkflowEvidence gitWorkflowEvidence
  ) {
    if (!gitScopeEnabled(decision.gitEnforcementScope())) {
      return "git enforcement scope disabled";
    }
    if (!decision.repoBackedWriteRun()) {
      return "run was not classified as repo-backed write";
    }
    if (decision.readOnlyMode()) {
      return "execution mode is read-only";
    }
    if (gitWorkflowEvidence == null || !gitWorkflowEvidence.gitRepository()) {
      return "workspace was not a git repository";
    }
    if (!diffPresent(diffText)) {
      return "no repository diff was produced";
    }
    return "repo-backed write run produced a repository diff";
  }

  public boolean commitCreated(ContextualToolPolicyService.GitWorkflowEvidence gitWorkflowEvidence) {
    return commitCount(gitWorkflowEvidence) > 0;
  }

  public int commitCount(ContextualToolPolicyService.GitWorkflowEvidence gitWorkflowEvidence) {
    if (gitWorkflowEvidence == null || !gitWorkflowEvidence.gitRepository()) {
      return 0;
    }
    return Math.max(0, gitWorkflowEvidence.commitCountSinceBase());
  }

  private boolean gitScopeEnabled(String scope) {
    return "repo-backed-write".equals(normalizeScope(scope));
  }

  private String normalizeScope(String value) {
    if (value == null || value.isBlank()) {
      return "repo-backed-write";
    }
    String normalized = value.strip().toLowerCase().replace("_", "-");
    return switch (normalized) {
      case "repo-backed-write" -> "repo-backed-write";
      case "disabled", "off", "none" -> "disabled";
      default -> "repo-backed-write";
    };
  }
}
