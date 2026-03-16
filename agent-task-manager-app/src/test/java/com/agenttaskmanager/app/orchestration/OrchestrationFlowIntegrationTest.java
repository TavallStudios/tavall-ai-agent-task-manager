package com.agenttaskmanager.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewResult;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewTask;
import com.agenttaskmanager.app.model.orchestration.OverseerTaskBatch;
import com.agenttaskmanager.app.model.orchestration.PatchDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.TaskAssignment;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerCheckIn;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import com.agenttaskmanager.app.model.validation.ValidationReport;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class OrchestrationFlowIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private ArtifactService artifactService;

  @Autowired
  private CleanupReviewService cleanupReviewService;

  @Autowired
  private DashboardSummaryService dashboardSummaryService;

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private OverseerOrchestrationService overseerOrchestrationService;

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @Autowired
  private TaskPoolService taskPoolService;

  @Autowired
  private WorkerLifecycleService workerLifecycleService;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.validation_violations").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.validation_reports").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.patch_decisions").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.cleanup_reviews").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.shared_task_context").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.task_artifacts").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_checkins").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_task_leases").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_tasks").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.agent_tasks WHERE task_kind = 'orchestration-batch'").update();
  }

  @Test
  void shouldRunHappyPathThroughCleanupAndValidation() {
    OverseerTaskBatch batch = overseerOrchestrationService.createTaskBatch(
        "agent-task-manager",
        "/srv/AgentTaskManager",
        "Integration batch",
        true,
        List.of("implementation", "validation")
    );

    TaskAssignment assignment = overseerOrchestrationService.assignNextWorkerTask(
        batch.taskId(),
        "integration-worker",
        WorkerTransportKind.LOCAL_CODEX_EXEC,
        "session-integration"
    );

    assertNotNull(assignment);
    assertEquals(batch.taskId(), assignment.taskId());

    WorkerCheckIn checkIn = workerLifecycleService.submitWorkerCheckIn(
        assignment.workerTaskId(),
        batch.taskId(),
        "integration-worker",
        TaskLifecycleStatus.CHECKED_IN,
        "Implementation worker started.",
        Map.of("step", "research")
    );

    assertEquals(TaskLifecycleStatus.CHECKED_IN, checkIn.status());

    sharedTaskContextService.storeSharedTaskContext(
        batch.taskId(),
        assignment.workerTaskId(),
        "research-note",
        "team",
        "Collected runtime notes.",
        Map.of("note", "Use integration tests only.")
    );

    assertFalse(sharedTaskContextService.loadSiblingTaskSummaries(batch.taskId(), assignment.workerTaskId()).isEmpty());

    var diffArtifact = artifactService.storeDiffArtifact(
        batch.taskId(),
        assignment.workerTaskId(),
        """
        diff --git a/Test.java b/Test.java
        +System.out.println("debug");
        """,
        Map.of("source", "integration-test")
    );

    CleanupReviewTask cleanupReviewTask = taskPoolService.createCleanupReviewTask(
        batch.taskId(),
        assignment.workerTaskId(),
        diffArtifact.artifactId()
    );
    CleanupReviewResult cleanupReviewResult = cleanupReviewService.runCleanupDiffReview(cleanupReviewTask.cleanupReviewId());

    assertEquals(TaskLifecycleStatus.NEEDS_REWORK, cleanupReviewResult.status());

    ValidationReport report = overseerOrchestrationService.validateBatch(
        batch.taskId(),
        assignment.workerTaskId(),
        Path.of("/srv/AgentTaskManager")
    );

    assertNotNull(report.reportId());
    assertFalse(report.violations().isEmpty());

    PatchDecisionRecord patchDecision = overseerOrchestrationService.decidePatch(
        batch.taskId(),
        assignment.workerTaskId(),
        report.reportId(),
        cleanupReviewTask.cleanupReviewId(),
        diffArtifact.artifactId(),
        false
    );

    assertEquals(TaskLifecycleStatus.NEEDS_REWORK, patchDecision.status());
    assertTrue(dashboardSummaryService.loadDashboardSummary().queuedTasks() >= 1);
  }

  @Test
  void shouldDetectDeadWorkersAndReassignTasks() {
    OverseerTaskBatch batch = overseerOrchestrationService.createTaskBatch(
        "agent-task-manager",
        "/srv/AgentTaskManager",
        "Timeout batch",
        true,
        List.of("implementation")
    );

    TaskAssignment assignment = overseerOrchestrationService.assignNextWorkerTask(
        batch.taskId(),
        "timeout-worker",
        WorkerTransportKind.LOCAL_CODEX_EXEC,
        "session-timeout"
    );

    jdbcClient.sql("""
            UPDATE agent_task_manager.worker_task_leases
            SET expires_at = now() - interval '5 seconds'
            WHERE worker_task_id = :workerTaskId
            """)
        .param("workerTaskId", assignment.workerTaskId())
        .update();

    var deadWorkers = overseerOrchestrationService.detectDeadWorkers();

    assertFalse(deadWorkers.isEmpty());
    assertEquals(TaskLifecycleStatus.REASSIGNED, taskPoolService.listWorkerTasks(batch.taskId()).getFirst().status());
  }
}
