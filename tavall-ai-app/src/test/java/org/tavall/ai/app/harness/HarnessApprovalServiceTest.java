package org.tavall.ai.app.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.harness.approval.HarnessApprovalGateResult;
import org.tavall.ai.app.harness.approval.HarnessApprovalService;
import org.tavall.ai.app.harness.routing.HarnessWorkerPlan;
import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.model.orchestration.WorkerType;
import org.tavall.ai.app.orchestration.ArtifactService;
import org.tavall.ai.app.orchestration.TaskPoolService;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class HarnessApprovalServiceTest extends IntegrationTestSupport {

  @Autowired
  private HarnessApprovalService harnessApprovalService;

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private TaskPoolService taskPoolService;

  @Autowired
  private ArtifactService artifactService;

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
    jdbcClient.sql("DELETE FROM agent_task_manager.overseer_decisions").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.agent_tasks WHERE task_kind = 'orchestration-batch'").update();
  }

  @Test
  void shouldSkipCodeOnlyApprovalGatesForRetrievalWorkers() {
    String taskId = taskPoolService.createPlannedTaskBatch(
        "tavall-ai",
        "/srv/AgentTaskManager",
        "Retrieval batch",
        true,
        List.of(new HarnessWorkerPlan(
            WorkerType.RETRIEVAL,
            "retrieval",
            "Retrieval worker for Retrieval batch",
            false,
            false,
            false,
            false
        ))
    ).taskId();
    String workerTaskId = taskPoolService.listWorkerTasks(taskId).getFirst().workerTaskId();

    HarnessApprovalGateResult result = harnessApprovalService.runApprovalGate(
        taskId,
        workerTaskId,
        Path.of("/srv/AgentTaskManager"),
        null,
        0,
        false,
        null
    );

    assertTrue(result.approved());
    assertEquals(TaskLifecycleStatus.COMPLETED, result.taskStatus());
    assertEquals("skipped", result.cleanup().status());
    assertEquals("skipped", result.validation().status());
    assertEquals("skipped", result.integrationTests().get("status"));
  }

  @Test
  void shouldExposeDeterministicCleanJavaStagesForCodeWorkers() {
    String taskId = taskPoolService.createPlannedTaskBatch(
        "tavall-ai",
        "/srv/AgentTaskManager",
        "Code batch",
        true,
        List.of(new HarnessWorkerPlan(
            WorkerType.CODE,
            "code",
            "Code worker for Code batch",
            true,
            true,
            false,
            true
        ))
    ).taskId();
    String workerTaskId = taskPoolService.listWorkerTasks(taskId).getFirst().workerTaskId();
    String diffArtifactId = artifactService.storeDiffArtifact(
        taskId,
        workerTaskId,
        """
        diff --git a/tavall-ai-core/src/main/java/com/agenttaskmanager/app/harness/approval/HarnessApprovalService.java b/tavall-ai-core/src/main/java/com/agenttaskmanager/app/harness/approval/HarnessApprovalService.java
        index 1234567..89abcde 100644
        --- a/tavall-ai-core/src/main/java/com/agenttaskmanager/app/harness/approval/HarnessApprovalService.java
        +++ b/tavall-ai-core/src/main/java/com/agenttaskmanager/app/harness/approval/HarnessApprovalService.java
        @@
        +// deterministic harness approval flow
        """,
        Map.of("source", "integration-test")
    ).artifactId();

    HarnessApprovalGateResult result = harnessApprovalService.runApprovalGate(
        taskId,
        workerTaskId,
        Path.of("/srv/AgentTaskManager"),
        diffArtifactId,
        0,
        false,
        null
    );

    assertTrue(result.patchScopeAllowed());
    assertNotNull(result.validation().lint());
    assertEquals("lint", result.validation().lint().stageName());
    assertNotNull(result.validation().sourceShape());
    assertEquals("source-shape", result.validation().sourceShape().stageName());
    assertNotNull(result.validation().architecture());
    assertEquals("architecture", result.validation().architecture().stageName());
    assertNotNull(result.validation().cycles());
    assertEquals("cycle-check", result.validation().cycles().stageName());
  }
}


