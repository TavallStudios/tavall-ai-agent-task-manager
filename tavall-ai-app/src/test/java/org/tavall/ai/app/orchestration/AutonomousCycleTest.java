package org.tavall.ai.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.model.orchestration.AutonomousCycleReport;
import org.tavall.ai.app.model.orchestration.OverseerTaskBatch;
import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class AutonomousCycleTest extends IntegrationTestSupport {

  @Autowired
  private AutonomousCycleService autonomousCycleService;

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private OverseerOrchestrationService overseerOrchestrationService;

  @Autowired
  private TaskPoolService taskPoolService;

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
  void shouldRunAutonomousCycleThroughApproval(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    OverseerTaskBatch batch = overseerOrchestrationService.createTaskBatch(
        "autonomy-integration",
        repoPath.toString(),
        "Autonomy integration batch",
        true,
        List.of("implementation")
    );

    AutonomousCycleReport report = autonomousCycleService.runCycle(repoPath);

    assertEquals(1, report.workerRuns());
    assertTrue(report.processedBatchIds().contains(batch.taskId()));
    assertFalse(report.patchDecisionIds().isEmpty());
    assertEquals(TaskLifecycleStatus.APPROVED, taskPoolService.listWorkerTasks(batch.taskId()).getFirst().status());
    assertTrue(report.completedBatchIds().contains(batch.taskId()));
    assertTrue(report.dashboardSummary().completedTasks() >= 1);
  }

  private Path initializeFixtureRepo(Path repoPath) throws Exception {
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(
        repoPath.resolve("README.md"),
        "# Autonomous Fixture\n",
        StandardCharsets.UTF_8
    );
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        """
        package example;

        public class FixtureApp {
        }
        """,
        StandardCharsets.UTF_8
    );
    run(repoPath, "git", "init", "-b", "main");
    run(repoPath, "git", "config", "user.email", "integration@example.com");
    run(repoPath, "git", "config", "user.name", "Integration Test");
    run(repoPath, "git", "add", ".");
    run(repoPath, "git", "commit", "-m", "Initial fixture");
    return repoPath;
  }

  private void run(Path repoPath, String... command) throws Exception {
    Process process = new ProcessBuilder(command)
        .directory(repoPath.toFile())
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();

    if (exitCode != 0) {
      throw new IOException(String.join(" ", command) + " failed: " + output);
    }
  }
}

