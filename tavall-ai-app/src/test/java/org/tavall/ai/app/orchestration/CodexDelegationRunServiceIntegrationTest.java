package org.tavall.ai.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class CodexDelegationRunServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private CodexDelegationRunService codexDelegationRunService;

  @Autowired
  private JdbcClient jdbcClient;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.codex_delegation_steps").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.codex_delegation_runs").update();
  }

  @Test
  void shouldPersistDelegationTimelineAndCompletion() {
    var started = codexDelegationRunService.startRun(
        null,
        "tavall-ai",
        "/srv/AgentTaskManager",
        "Delegation happy path",
        Map.of("requestedBy", "integration-test")
    );

    codexDelegationRunService.appendEvent(
        started.run().runId(),
        "spawn-sub-agent",
        TaskLifecycleStatus.RUNNING,
        "Spawned retrieval helper.",
        Map.of("agentRole", "retrieval")
    );
    codexDelegationRunService.appendEvent(
        started.run().runId(),
        "wait-sub-agent",
        TaskLifecycleStatus.CHECKED_IN,
        "Waiting for helper result.",
        Map.of("state", "waiting")
    );

    var completed = codexDelegationRunService.completeRun(
        started.run().runId(),
        TaskLifecycleStatus.COMPLETED,
        "Run completed with no diff.",
        Map.of("diffPresent", false)
    );

    assertEquals(TaskLifecycleStatus.COMPLETED, completed.run().status());
    assertTrue(completed.steps().size() >= 4);
    assertTrue(completed.steps().stream().anyMatch(step -> "spawn-sub-agent".equals(step.eventType())));
    assertTrue(completed.steps().stream().anyMatch(step -> "result".equals(step.eventType())));
  }

  @Test
  void shouldPersistFailureAndSupportStatusFiltering() {
    var started = codexDelegationRunService.startRun(
        null,
        "tavall-ai",
        "/srv/AgentTaskManager",
        "Delegation failure path",
        Map.of()
    );

    codexDelegationRunService.completeRun(
        started.run().runId(),
        TaskLifecycleStatus.FAILED,
        "Sub-agent failed and requires manual follow-up.",
        Map.of("failureKind", "sub-agent-error")
    );

    var failedRuns = codexDelegationRunService.listRuns(10, "FAILED");
    assertFalse(failedRuns.isEmpty());
    assertTrue(failedRuns.stream().anyMatch(run -> run.runId().equals(started.run().runId())));
  }

  @Test
  void shouldPersistTimeoutOrCancelOutcome() {
    var started = codexDelegationRunService.startRun(
        null,
        "tavall-ai",
        "/srv/AgentTaskManager",
        "Delegation timeout path",
        Map.of()
    );

    var timedOut = codexDelegationRunService.completeRun(
        started.run().runId(),
        TaskLifecycleStatus.DEAD,
        "Delegation timed out before sub-agent completion.",
        Map.of("failureKind", "timeout")
    );

    assertEquals(TaskLifecycleStatus.DEAD, timedOut.run().status());
    assertTrue(timedOut.steps().stream().anyMatch(step -> "failure".equals(step.eventType())));
  }
}


