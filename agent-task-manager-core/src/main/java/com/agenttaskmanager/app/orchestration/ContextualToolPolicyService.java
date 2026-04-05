package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.config.ToolPolicyProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ContextualToolPolicyService {

  private final GitWorkflowPolicySupport gitWorkflowPolicySupport;
  private final NativeWindowsShellPolicySupport nativeWindowsShellPolicySupport;
  private final ToolPolicyProperties properties;
  private final ToolPolicyNarrativeRenderer renderer;

  public ContextualToolPolicyService(
      GitWorkflowPolicySupport gitWorkflowPolicySupport,
      NativeWindowsShellPolicySupport nativeWindowsShellPolicySupport,
      ToolPolicyProperties properties,
      ToolPolicyNarrativeRenderer renderer
  ) {
    this.gitWorkflowPolicySupport = gitWorkflowPolicySupport;
    this.nativeWindowsShellPolicySupport = nativeWindowsShellPolicySupport;
    this.properties = properties;
    this.renderer = renderer;
  }

  public String buildPolicy(String executionMode, String promptText, boolean workerRun) {
    return buildPolicy(executionMode, promptText, workerRun, workerRun);
  }

  public ToolPolicyDecision decide(String executionMode, String promptText, boolean workerRun) {
    return decide(executionMode, promptText, workerRun, workerRun);
  }

  public String buildPolicy(
      String executionMode,
      String promptText,
      boolean workerRun,
      boolean repoBackedWriteRun
  ) {
    ToolPolicyDecision decision = decide(executionMode, promptText, workerRun, repoBackedWriteRun);
    return renderer.render(decision);
  }

  public ToolPolicyDecision decide(
      String executionMode,
      String promptText,
      boolean workerRun,
      boolean repoBackedWriteRun
  ) {
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
    if (repoBackedWriteRun && !readOnly && gitScopeEnabled()) {
      required = true;
      requiredCalls.add("plangitcommit");
      requiredCalls.add("preparegitbranch");
      requiredCalls.add("creategitcommit");
      rationale.add("repo-backed write policy requires auditable git branch planning and commit workflow when repository changes are produced");
    }
    if (!required && smallTalkIntent) {
      rationale.add("prompt appears conversational; tool calls can be skipped");
    }
    if (rationale.isEmpty()) {
      rationale.add("no strong repository signal detected");
    }

    return new ToolPolicyDecision(
        required,
        readOnly,
        workerRun,
        repoBackedWriteRun,
        properties.getGitEnforcementScope(),
        nativeWindowsShellPolicySupport.enforcementMode(),
        requiredCalls,
        rationale
    );
  }

  public ToolPolicyAudit audit(ToolPolicyDecision decision, Set<String> observedCalls) {
    return audit(
        decision,
        observationsFromSignatures(observedCalls),
        "",
        "",
        new GitWorkflowEvidence(false, "", "", "", 0, "", ""),
        HarnessMemoryEvidence.disabled(),
        CodexRuntimePlatform.NON_WINDOWS
    );
  }

  public ToolPolicyAudit audit(ToolPolicyDecision decision, Set<String> observedCalls, String outputText) {
    return audit(
        decision,
        observationsFromSignatures(observedCalls),
        outputText,
        "",
        new GitWorkflowEvidence(false, "", "", "", 0, "", ""),
        HarnessMemoryEvidence.disabled(),
        CodexRuntimePlatform.NON_WINDOWS
    );
  }

  public ToolPolicyAudit audit(
      ToolPolicyDecision decision,
      Set<String> observedCalls,
      String outputText,
      String diffText,
      GitWorkflowEvidence gitWorkflowEvidence
  ) {
    return audit(
        decision,
        observationsFromSignatures(observedCalls),
        outputText,
        diffText,
        gitWorkflowEvidence,
        HarnessMemoryEvidence.disabled(),
        CodexRuntimePlatform.NON_WINDOWS
    );
  }

  public ToolPolicyAudit audit(
      ToolPolicyDecision decision,
      Set<String> observedCalls,
      String outputText,
      String diffText,
      GitWorkflowEvidence gitWorkflowEvidence,
      HarnessMemoryEvidence harnessMemoryEvidence
  ) {
    return audit(
        decision,
        observationsFromSignatures(observedCalls),
        outputText,
        diffText,
        gitWorkflowEvidence,
        harnessMemoryEvidence,
        CodexRuntimePlatform.NON_WINDOWS
    );
  }

  public ToolPolicyAudit audit(
      ToolPolicyDecision decision,
      Set<CodexToolCallObservation> observedCalls,
      String outputText,
      String diffText,
      GitWorkflowEvidence gitWorkflowEvidence,
      HarnessMemoryEvidence harnessMemoryEvidence,
      CodexRuntimePlatform runtimePlatform
  ) {
    Set<String> normalizedObserved = new LinkedHashSet<>();
    Set<CodexToolCallObservation> normalizedObservations = new LinkedHashSet<>();
    for (CodexToolCallObservation observed : observedCalls) {
      String normalizedSignature = normalizeObservedSignature(observed.signature());
      String toolName = observed.toolName() == null ? "" : observed.toolName().strip();
      if (normalizedSignature.isBlank() && !toolName.isBlank()) {
        normalizedSignature = normalizeObservedSignature(toolName);
      }
      if (!normalizedSignature.isBlank()) {
        normalizedObserved.add(normalizedSignature);
        normalizedObservations.add(new CodexToolCallObservation(normalizedSignature, toolName));
      }
    }
    Set<String> requiredCalls = gitWorkflowPolicySupport.filterRequiredCalls(decision, diffText, gitWorkflowEvidence);
    Set<String> missing = new LinkedHashSet<>();
    if (decision.required()) {
      for (String requiredCall : requiredCalls) {
        if (!normalizedObserved.contains(requiredCall)) {
          missing.add(requiredCall);
        }
      }
    }
    Set<String> violations = gitWorkflowPolicySupport.validate(decision, diffText, gitWorkflowEvidence, normalizedObserved);
    validateHarnessMemory(decision, harnessMemoryEvidence, violations);
    Set<String> forbiddenToolCalls = nativeWindowsShellPolicySupport.validate(
        runtimePlatform,
        normalizedObservations,
        violations
    );
    boolean gitWorkflowRequired = gitWorkflowPolicySupport.gitWorkflowRequired(decision, diffText, gitWorkflowEvidence);
    boolean diffPresent = gitWorkflowPolicySupport.diffPresent(diffText);
    String gitEnforcementReason = gitWorkflowPolicySupport.enforcementReason(decision, diffText, gitWorkflowEvidence);
    return new ToolPolicyAudit(
        missing.isEmpty() && violations.isEmpty(),
        missing,
        normalizedObserved,
        violations,
        harnessMemoryEvidence.memoryStatus(),
        harnessMemoryEvidence.qdrantHealth(),
        gitWorkflowRequired,
        diffPresent,
        gitEnforcementReason,
        gitWorkflowPolicySupport.commitCreated(gitWorkflowEvidence),
        gitWorkflowPolicySupport.commitCount(gitWorkflowEvidence),
        runtimePlatform.value(),
        nativeWindowsShellPolicySupport.enforcementMode(),
        forbiddenToolCalls
    );
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

  private void validateHarnessMemory(
      ToolPolicyDecision decision,
      HarnessMemoryEvidence harnessMemoryEvidence,
      Set<String> violations
  ) {
    if (!decision.required() || !harnessMemoryEvidence.enabled()) {
      return;
    }
    String enforcementMode = normalizeMemoryMode(properties.getMemoryEnforcementMode());
    if ("auto-only".equals(enforcementMode)) {
      return;
    }
    boolean qdrantHealthy = "HEALTHY".equalsIgnoreCase(harnessMemoryEvidence.qdrantHealth());
    boolean failClosed = "fail-closed-always".equals(enforcementMode);
    if (failClosed && !harnessMemoryEvidence.memorySatisfied()) {
      violations.add("Harness memory evidence is required before completion.");
      return;
    }
    if (qdrantHealthy && !harnessMemoryEvidence.memorySatisfied()) {
      violations.add("Harness memory context was required but not confirmed while Qdrant was healthy.");
    }
  }

  private String normalizeMemoryMode(String value) {
    String normalized = normalize(value);
    if (normalized.isBlank()) {
      return "auto-gate";
    }
    return switch (normalized) {
      case "autogate" -> "auto-gate";
      case "autoonly" -> "auto-only";
      case "failclosedalways" -> "fail-closed-always";
      default -> "auto-gate";
    };
  }

  private boolean gitScopeEnabled() {
    String normalized = properties.getGitEnforcementScope() == null
        ? ""
        : properties.getGitEnforcementScope().strip().toLowerCase(Locale.ROOT).replace("_", "-");
    return normalized.isBlank() || "repo-backed-write".equals(normalized);
  }

  private Set<CodexToolCallObservation> observationsFromSignatures(Set<String> observedCalls) {
    Set<CodexToolCallObservation> observations = new LinkedHashSet<>();
    for (String observedCall : observedCalls) {
      observations.add(new CodexToolCallObservation(observedCall, observedCall));
    }
    return observations;
  }

  public record ToolPolicyDecision(
      boolean required,
      boolean readOnlyMode,
      boolean workerRun,
      boolean repoBackedWriteRun,
      String gitEnforcementScope,
      String nativeWindowsShellEnforcementMode,
      Set<String> requiredCalls,
      List<String> rationale
  ) {
  }

  public record ToolPolicyAudit(
      boolean passed,
      Set<String> missingCalls,
      Set<String> observedCalls,
      Set<String> violations,
      String memoryStatus,
      String qdrantHealth,
      boolean gitWorkflowRequired,
      boolean diffPresent,
      String gitEnforcementReason,
      boolean commitCreated,
      int commitCount,
      String runtimePlatform,
      String nativeWindowsShellEnforcementMode,
      Set<String> forbiddenToolCalls
  ) {
  }

  public record HarnessMemoryEvidence(
      boolean enabled,
      boolean memorySatisfied,
      String memoryStatus,
      String qdrantHealth
  ) {
    public static HarnessMemoryEvidence disabled() {
      return new HarnessMemoryEvidence(false, false, "disabled", "unknown");
    }
  }

  public record GitWorkflowEvidence(
      boolean gitRepository,
      String branchName,
      String baseCommitHash,
      String headCommitHash,
      int commitCountSinceBase,
      String headSubject,
      String headBody
  ) {
  }
}
