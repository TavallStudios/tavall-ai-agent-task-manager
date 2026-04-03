package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.config.ToolPolicyProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ContextualToolPolicyService {
  private static final Set<String> GENERIC_GIT_MUTATION_CALLS = Set.of(
      "gitadd",
      "gitcheckout",
      "gitcommit",
      "gitcreatebranch",
      "gitreset"
  );

  private final ToolPolicyProperties properties;

  public ContextualToolPolicyService(ToolPolicyProperties properties) {
    this.properties = properties;
  }

  public String buildPolicy(String executionMode, String promptText, boolean workerRun) {
    ToolPolicyDecision decision = decide(executionMode, promptText, workerRun);
    return render(decision);
  }

  public ToolPolicyDecision decide(String executionMode, String promptText, boolean workerRun) {
    String normalizedPrompt = normalize(promptText);
    boolean readOnly = "read-only".equalsIgnoreCase(normalize(executionMode));
    boolean javaIntent = hasAny(normalizedPrompt, "java", "spring", "maven", "gradle", "archunit", "spoon", "package", "class", "interface");
    boolean repoIntent = hasAny(
        normalizedPrompt,
        "file",
        "repo",
        "repository",
        "codebase",
        "source",
        "where",
        "find",
        "search",
        "grep",
        "trace",
        "bug",
        "fix",
        "refactor",
        "compile",
        "build",
        "test",
        "failing",
        "error"
    );
    boolean orchestrationIntent = hasAny(
        normalizedPrompt,
        "worker",
        "task",
        "batch",
        "approval",
        "cleanup",
        "validation",
        "artifact",
        "patch",
        "harness"
    );
    boolean smallTalkIntent = isSmallTalk(normalizedPrompt);

    Set<String> requiredCalls = new LinkedHashSet<>();
    List<String> rationale = new ArrayList<>();
    boolean required = workerRun;

    if (properties.isForceHarnessForAllPrompts() && !smallTalkIntent) {
      required = true;
      requiredCalls.add("runharnesstoolbundle(repo-context)");
      rationale.add("global tool policy forces harness repo-context before completion");
    }
    if (!smallTalkIntent) {
      for (String configuredCall : properties.getForcedToolCalls()) {
        String normalizedCall = normalizeObservedSignature(configuredCall);
        if (!normalizedCall.isBlank()) {
          required = true;
          requiredCalls.add(normalizedCall);
        }
      }
    }

    if (workerRun) {
      requiredCalls.add("runharnesstoolbundle(worker-context)");
      requiredCalls.add("runharnesstoolbundle(repo-context)");
      rationale.add("worker-run policy requires deterministic harness context before execution");
      if (!readOnly) {
        requiredCalls.add("plangitcommit");
        requiredCalls.add("preparegitbranch");
        requiredCalls.add("creategitcommit");
        rationale.add("worker-run edit policy requires auditable git branch planning and commit workflow for repository changes");
      }
    }
    if (repoIntent || orchestrationIntent) {
      required = true;
      requiredCalls.add("runharnesstoolbundle(repo-context)");
      rationale.add("prompt indicates repository or task-state inspection");
    }
    if (orchestrationIntent) {
      requiredCalls.add("runharnesstoolbundle(worker-context)");
      rationale.add("prompt references worker/task orchestration state");
    }
    if (javaIntent) {
      required = true;
      requiredCalls.add("runharnesstoolbundle(repo-context)");
      rationale.add("prompt indicates Java changes that require repo context before local validation runs");
    }
    if (!required && !smallTalkIntent && !readOnly) {
      required = true;
      requiredCalls.add("runharnesstoolbundle(repo-context)");
      rationale.add("non-trivial edit mode defaults to repository context first");
    }
    if (!required && smallTalkIntent) {
      rationale.add("prompt appears conversational; tool calls can be skipped");
    }
    if (rationale.isEmpty()) {
      rationale.add("no strong repository signal detected");
    }

    return new ToolPolicyDecision(required, readOnly, requiredCalls, rationale);
  }

  public ToolPolicyAudit audit(ToolPolicyDecision decision, Set<String> observedCalls) {
    return audit(decision, observedCalls, "", "", new GitWorkflowEvidence(false, "", "", "", ""));
  }

  public ToolPolicyAudit audit(ToolPolicyDecision decision, Set<String> observedCalls, String outputText) {
    return audit(decision, observedCalls, outputText, "", new GitWorkflowEvidence(false, "", "", "", ""));
  }

  public ToolPolicyAudit audit(
      ToolPolicyDecision decision,
      Set<String> observedCalls,
      String outputText,
      String diffText,
      GitWorkflowEvidence gitWorkflowEvidence
  ) {
    Set<String> normalizedObserved = new LinkedHashSet<>();
    for (String observed : observedCalls) {
      String normalized = normalizeObservedSignature(observed);
      if (!normalized.isBlank()) {
        normalizedObserved.add(normalized);
      }
    }
    Set<String> requiredCalls = new LinkedHashSet<>(decision.requiredCalls());
    if (!requiresGitWorkflow(diffText, gitWorkflowEvidence)) {
      requiredCalls.remove("plangitcommit");
      requiredCalls.remove("preparegitbranch");
      requiredCalls.remove("creategitcommit");
    }
    Set<String> missing = new LinkedHashSet<>();
    if (decision.required()) {
      for (String requiredCall : requiredCalls) {
        if (!normalizedObserved.contains(requiredCall)) {
          missing.add(requiredCall);
        }
      }
    }
    Set<String> violations = validateGitWorkflow(diffText, gitWorkflowEvidence, normalizedObserved);
    return new ToolPolicyAudit(missing.isEmpty() && violations.isEmpty(), missing, normalizedObserved, violations);
  }

  public String normalizeObservedSignature(String signature) {
    String normalized = normalize(signature);
    if (normalized.isBlank()) {
      return "";
    }
    if ("runharnesstoolbundle".equals(normalized)) {
      return normalized;
    }
    if (normalized.startsWith("runharnesstoolbundle(") && normalized.endsWith(")")) {
      return normalized;
    }
    return normalized;
  }

  private String render(ToolPolicyDecision decision) {
    Map<String, String> prettyNames = new LinkedHashMap<>();
    prettyNames.put("runharnesstoolbundle(worker-context)", "runHarnessToolBundle(worker-context)");
    prettyNames.put("runharnesstoolbundle(repo-context)", "runHarnessToolBundle(repo-context)");
    prettyNames.put("runharnesstoolbundle(java-context)", "runHarnessToolBundle(java-context)");
    prettyNames.put("preparegitbranch", "prepareGitBranch");
    prettyNames.put("creategitcommit", "createGitCommit");
    prettyNames.put("plangitcommit", "planGitCommit");

    List<String> lines = new ArrayList<>();
    lines.add("Contextual tool policy (auto-inferred):");
    lines.add("- decision: " + (decision.required() ? "REQUIRED" : "OPTIONAL"));
    lines.add("- executionMode: " + (decision.readOnlyMode() ? "read-only" : "workspace-write"));
    lines.add("- rationale: " + String.join("; ", decision.rationale()));
    if (decision.required()) {
      lines.add("- required sequence:");
      for (String call : decision.requiredCalls()) {
        lines.add("  - " + prettyNames.getOrDefault(call, call));
      }
      lines.add("- do not finalize until required tool calls complete (or explicitly fail with reason)");
    } else {
      lines.add("- tool calls may be skipped unless new evidence requires repository verification");
    }
    return String.join("\n", lines);
  }

  private static boolean isSmallTalk(String prompt) {
    if (prompt.isBlank()) {
      return true;
    }
    String compact = prompt.strip();
    if (compact.length() > 120) {
      return false;
    }
    return hasAny(compact, "hi", "hello", "thanks", "thankyou", "goodmorning", "goodafternoon");
  }

  private static boolean hasAny(String value, String... keywords) {
    for (String keyword : keywords) {
      if (value.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String value) {
    return value == null
        ? ""
        : value.toLowerCase(Locale.ROOT).strip().replace(" ", "").replace("_", "").replace("\"", "");
  }

  private boolean requiresGitWorkflow(String diffText, GitWorkflowEvidence gitWorkflowEvidence) {
    return gitWorkflowEvidence != null
        && gitWorkflowEvidence.gitRepository()
        && diffText != null
        && !diffText.isBlank();
  }

  private Set<String> validateGitWorkflow(
      String diffText,
      GitWorkflowEvidence gitWorkflowEvidence,
      Set<String> normalizedObserved
  ) {
    Set<String> violations = new LinkedHashSet<>();
    if (!requiresGitWorkflow(diffText, gitWorkflowEvidence)) {
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

  public record ToolPolicyDecision(
      boolean required,
      boolean readOnlyMode,
      Set<String> requiredCalls,
      List<String> rationale
  ) {
  }

  public record ToolPolicyAudit(
      boolean passed,
      Set<String> missingCalls,
      Set<String> observedCalls,
      Set<String> violations
  ) {
  }

  public record GitWorkflowEvidence(
      boolean gitRepository,
      String branchName,
      String headCommitHash,
      String headSubject,
      String headBody
  ) {
  }
}
