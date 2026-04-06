package org.tavall.ai.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.support.IntegrationTestSupport;
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
  void shouldRequireGitWorkflowCallsForRepoBackedNonWorkerWrites() {
    ContextualToolPolicyService.ToolPolicyDecision decision = service.decide(
        "run-tests",
        "Fix failing repo build",
        false,
        true
    );

    assertTrue(decision.requiredCalls().contains("plangitcommit"));
    assertTrue(decision.requiredCalls().contains("preparegitbranch"));
    assertTrue(decision.requiredCalls().contains("creategitcommit"));
    assertTrue(decision.repoBackedWriteRun());
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
        new ContextualToolPolicyService.GitWorkflowEvidence(true, "", "", "", 0, "", "")
    );

    assertFalse(audit.passed());
    assertTrue(audit.missingCalls().contains("plangitcommit"));
    assertTrue(audit.missingCalls().contains("preparegitbranch"));
    assertTrue(audit.missingCalls().contains("creategitcommit"));
    assertTrue(audit.gitWorkflowRequired());
    assertTrue(audit.diffPresent());
    assertEquals("repo-backed write run produced a repository diff", audit.gitEnforcementReason());
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
            "base123",
            "abc123",
            1,
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
  void shouldFailAuditWhenGitWorkflowDidNotCreateANewCommit() {
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
            "createGitCommit"
        ),
        "Completed worker run.",
        """
        diff --git a/README.md b/README.md
        +workflow
        """,
        new ContextualToolPolicyService.GitWorkflowEvidence(
            true,
            "atm-harness-tj-v1",
            "base123",
            "base123",
            0,
            "Changed: Existing commit",
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
    assertTrue(audit.gitWorkflowRequired());
    assertFalse(audit.commitCreated());
    assertEquals(0, audit.commitCount());
    assertTrue(audit.violations().stream().anyMatch(violation -> violation.contains("create a new commit")));
  }

  @Test
  void shouldFailAuditWhenGitWorkflowCreatesMultipleCommitsForOnePrompt() {
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
            "createGitCommit"
        ),
        "Completed worker run.",
        """
        diff --git a/README.md b/README.md
        +workflow
        """,
        new ContextualToolPolicyService.GitWorkflowEvidence(
            true,
            "atm-harness-tj-v1",
            "base123",
            "head789",
            2,
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
    assertTrue(audit.commitCreated());
    assertEquals(2, audit.commitCount());
    assertTrue(audit.violations().stream().anyMatch(violation -> violation.contains("exactly one new commit")));
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

  @Test
  void shouldFailAuditWhenHarnessMemoryIsMissingWhileQdrantIsHealthy() {
    ContextualToolPolicyService.ToolPolicyDecision decision = service.decide(
        "edit",
        "Implement repository memory enforcement",
        false
    );

    ContextualToolPolicyService.ToolPolicyAudit audit = service.audit(
        decision,
        Set.of("runHarnessToolBundle(repo-context)"),
        "Completed worker run.",
        "",
        new ContextualToolPolicyService.GitWorkflowEvidence(false, "", "", "", 0, "", ""),
        new ContextualToolPolicyService.HarnessMemoryEvidence(true, false, "missing", "HEALTHY")
    );

    assertFalse(audit.passed());
    assertEquals("missing", audit.memoryStatus());
    assertEquals("HEALTHY", audit.qdrantHealth());
    assertTrue(audit.violations().stream().anyMatch(violation -> violation.contains("Harness memory context")));
  }

  @Test
  void shouldAllowMissingHarnessMemoryWhenQdrantIsDegraded() {
    ContextualToolPolicyService.ToolPolicyDecision decision = service.decide(
        "edit",
        "Implement repository memory enforcement",
        false
    );

    ContextualToolPolicyService.ToolPolicyAudit audit = service.audit(
        decision,
        Set.of("runHarnessToolBundle(repo-context)"),
        "Completed worker run.",
        "",
        new ContextualToolPolicyService.GitWorkflowEvidence(false, "", "", "", 0, "", ""),
        new ContextualToolPolicyService.HarnessMemoryEvidence(true, false, "degraded", "DEGRADED")
    );

    assertTrue(audit.passed());
    assertEquals("degraded", audit.memoryStatus());
    assertEquals("DEGRADED", audit.qdrantHealth());
  }
}

