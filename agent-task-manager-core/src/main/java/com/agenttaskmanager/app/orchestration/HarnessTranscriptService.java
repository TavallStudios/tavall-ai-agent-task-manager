package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.harness.cleanjava.symbol.JavaSymbolRunContext;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HarnessTranscriptService {

  public String bootstrapSummary(
      String memoryStatus,
      String qdrantHealth,
      ContextualToolPolicyService.ToolPolicyDecision decision,
      JavaSymbolRunContext javaSymbolRunContext
  ) {
    return "Harness bootstrap fired. memoryStatus=" + blank(memoryStatus)
        + ", qdrantHealth=" + blank(qdrantHealth)
        + ", javaSymbolStatus=" + javaStatus(javaSymbolRunContext)
        + ", repoBackedWriteRun=" + decision.repoBackedWriteRun()
        + ", readOnlyMode=" + decision.readOnlyMode()
        + ".";
  }

  public String toolPolicySummary(ContextualToolPolicyService.ToolPolicyDecision decision) {
    return "Harness tool policy active. requiredCalls="
        + join(decision.requiredCalls().stream().toList())
        + ", gitEnforcementScope="
        + blank(decision.gitEnforcementScope())
        + ", nativeWindowsShellEnforcementMode="
        + blank(decision.nativeWindowsShellEnforcementMode())
        + ", rationale="
        + join(decision.rationale())
        + ".";
  }

  public String toolCallSummary(String signature) {
    return "Observed tool call: " + blank(signature);
  }

  public String semanticSyncSummary(Map<String, Object> syncResult) {
    if (syncResult == null || syncResult.isEmpty()) {
      return "Semantic sync status unavailable.";
    }
    return "Semantic sync fired. status=" + value(syncResult, "status")
        + ", upsertedFiles=" + value(syncResult, "upsertedFiles")
        + ", deletedFiles=" + value(syncResult, "deletedFiles")
        + ", upsertedJavaSymbols=" + value(syncResult, "upsertedJavaSymbols")
        + ".";
  }

  public String gitWorkflowSummary(
      ContextualToolPolicyService.ToolPolicyAudit audit,
      GitWorktreeManager.GitHeadState gitHeadState
  ) {
    return "Git workflow status. required=" + audit.gitWorkflowRequired()
        + ", commitCreated=" + audit.commitCreated()
        + ", commitCount=" + audit.commitCount()
        + ", branch=" + blank(gitHeadState.branchName())
        + ", headCommit=" + blank(gitHeadState.headCommitHash())
        + ", reason=" + blank(audit.gitEnforcementReason())
        + ".";
  }

  private String javaStatus(JavaSymbolRunContext javaSymbolRunContext) {
    return javaSymbolRunContext == null ? "unavailable" : blank(javaSymbolRunContext.status());
  }

  private String join(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "<none>";
    }
    return String.join(", ", values.stream().map(this::blank).toList());
  }

  private String value(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    return value == null ? "0" : String.valueOf(value).strip();
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? "<none>" : value.strip();
  }
}
