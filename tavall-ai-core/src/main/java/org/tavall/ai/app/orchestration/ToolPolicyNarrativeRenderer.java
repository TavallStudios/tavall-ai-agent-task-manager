package org.tavall.ai.app.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ToolPolicyNarrativeRenderer {

  public String render(ContextualToolPolicyService.ToolPolicyDecision decision) {
    Map<String, String> prettyNames = new LinkedHashMap<>();
    prettyNames.put("runharnesstoolbundle(worker-context)", "runHarnessToolBundle(worker-context)");
    prettyNames.put("runharnesstoolbundle(repo-context)", "runHarnessToolBundle(repo-context)");
    prettyNames.put("runharnesstoolbundle(language-context)", "runHarnessToolBundle(language-context)");
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
}

