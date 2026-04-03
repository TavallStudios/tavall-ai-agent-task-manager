package com.agenttaskmanager.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContextualToolPolicyServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private ContextualToolPolicyService service;

  @Test
  void shouldRequireJavaContextCallsWhenPromptTargetsJavaChanges() {
    ContextualToolPolicyService.ToolPolicyDecision decision = service.decide(
        "edit",
        "Fix Java service wiring and failing Maven tests",
        false
    );

    assertTrue(decision.required());
    assertTrue(decision.requiredCalls().contains("runharnesstoolbundle(repo-context)"));
    assertFalse(decision.requiredCalls().contains("loadcleanjavataskcontext"));
    assertFalse(decision.requiredCalls().contains("runcleanjavaharness"));
  }

  @Test
  void shouldFailAuditWhenRequiredCallsAreMissing() {
    ContextualToolPolicyService.ToolPolicyDecision decision = service.decide(
        "edit",
        "Fix Java service wiring and failing Maven tests",
        false
    );
    ContextualToolPolicyService.ToolPolicyAudit audit = service.audit(decision, Set.of("runHarnessToolBundle(repo-context)"));

    assertTrue(audit.passed());
    assertFalse(audit.missingCalls().contains("loadcleanjavataskcontext"));
  }

  @Test
  void shouldForceHarnessRepoContextForNonSmallTalkPrompts() {
    ContextualToolPolicyService.ToolPolicyDecision decision = service.decide(
        "read-only",
        "Summarize current implementation constraints",
        false
    );

    assertTrue(decision.required());
    assertTrue(decision.requiredCalls().contains("runharnesstoolbundle(repo-context)"));
  }

  @Test
  void shouldRequireGitWorkflowCallsForWorkerEdits() {
    ContextualToolPolicyService.ToolPolicyDecision decision = service.decide(
        "edit",
        "Implement git workflow enforcement for worker edits",
        true
    );

    assertTrue(decision.requiredCalls().contains("plangitcommit"));
    assertTrue(decision.requiredCalls().contains("preparegitbranch"));
    assertTrue(decision.requiredCalls().contains("creategitcommit"));
  }

  @Test
  void shouldFailAuditWhenGitWorkflowCallsAreMissingForDiffProducingWorkerRuns() {
    ContextualToolPolicyService.ToolPolicyDecision decision = service.decide(
        "edit",
        "Implement git workflow enforcement for worker edits",
        true
    );

    ContextualToolPolicyService.ToolPolicyAudit audit = service.audit(
        decision,
        Set.of("runHarnessToolBundle(worker-context)", "runHarnessToolBundle(repo-context)"),
        "Completed worker run.",
        """
        diff --git a/README.md b/README.md
        +workflow
        """,
        new ContextualToolPolicyService.GitWorkflowEvidence(true, "", "", "", "")
    );

    assertFalse(audit.passed());
    assertTrue(audit.missingCalls().contains("plangitcommit"));
    assertTrue(audit.missingCalls().contains("preparegitbranch"));
    assertTrue(audit.missingCalls().contains("creategitcommit"));
  }

  @Test
  void shouldFailAuditWhenGenericGitMutationToolsAreObserved() {
    ContextualToolPolicyService.ToolPolicyDecision decision = service.decide(
        "edit",
        "Implement git workflow enforcement for worker edits",
        true
    );

    ContextualToolPolicyService.ToolPolicyAudit audit = service.audit(
        decision,
        Set.of(
            "runHarnessToolBundle(worker-context)",
            "runHarnessToolBundle(repo-context)",
            "planGitCommit",
            "prepareGitBranch",
            "createGitCommit",
            "git_commit"
        ),
        "Completed worker run.",
        """
        diff --git a/README.md b/README.md
        +workflow
        """,
        new ContextualToolPolicyService.GitWorkflowEvidence(
            true,
            "atm-harness-tj-v1",
            "abc123",
            "Changed: Workflow update",
            """
            What Changed:
            workflow

            Why:
            reason

            Verification:
            status
            """
        )
    );

    assertFalse(audit.passed());
    assertTrue(audit.violations().stream().anyMatch(violation -> violation.contains("gitcommit")));
  }

  @Test
  void shouldRequireCleanJavaHarnessWhenOutputContainsJavaCode() {
    ContextualToolPolicyService.ToolPolicyDecision decision = service.decide(
        "read-only",
        "hello",
        false
    );
    ContextualToolPolicyService.ToolPolicyAudit audit = service.audit(
        decision,
        Set.of("runHarnessToolBundle(repo-context)"),
        """
            ```java
            public class OutputSnippet {}
            ```
            """
    );

    assertTrue(audit.passed());
    assertFalse(audit.missingCalls().contains("runcleanjavaharness"));
  }
}
