package org.tavall.ai.app.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.harness.intake.HarnessTaskIntakeService;
import org.tavall.ai.app.harness.intake.ParentTaskRequest;
import org.tavall.ai.app.harness.intake.ParentTaskType;
import org.tavall.ai.app.harness.state.HarnessStateSnapshot;
import org.tavall.ai.app.retrieval.SemanticMemoryService;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class HarnessTaskIntakeTest extends IntegrationTestSupport {

  @Autowired
  private HarnessTaskIntakeService harnessTaskIntakeService;

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private SemanticMemoryService semanticMemoryService;

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
  void shouldIntakeParentTaskIntoTypedHarnessWorkers(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    HarnessStateSnapshot snapshot = harnessTaskIntakeService.intakeTask(new ParentTaskRequest(
        "task-1042",
        ParentTaskType.DEBUG_ISSUE,
        "Fix broken output",
        "Debug the stale output and missing cleanup review state.",
        repoPath.toString(),
        "HIGH",
        "TJ",
        true,
        false,
        true,
        List.of(),
        List.of("src/main/java/com/agenttaskmanager/app/orchestration/LocalCodexWorkerTransport.java"),
        "HEAD~1",
        "HEAD",
        Map.of("diffId", "diff-88"),
        Map.of("runState", "active"),
        Map.of("docs", List.of("AGENTS.md", "ARCHITECTURE.md")),
        Map.of("screenshots", 1),
        Map.of("source", "integration-test")
    ));

    Set<String> workerTypes = snapshot.taskSchema().workerTasks().stream()
        .map(workerTask -> workerTask.workerType().name())
        .collect(Collectors.toSet());

    assertEquals(Set.of("CODE", "CLEANUP", "COMPUTER_USE", "RETRIEVAL"), workerTypes);
    assertEquals(4L, snapshot.persistenceModel().queueDepth());
    assertTrue(snapshot.taskSchema().sharedTaskContext().stream()
        .map(context -> context.contextKey())
        .collect(Collectors.toSet())
        .containsAll(Set.of("harness-parent-task", "harness-routing-plan", "harness-codebase-input")));
    assertTrue(snapshot.dashboardModel().workerTypeCounts().containsKey("CODE"));
    assertTrue(semanticMemoryService.searchProject(
        snapshot.taskSchema().batch().projectKey(),
        "cleanup review state",
        10,
        Map.of()
    ).stream().anyMatch(item -> "TASK_HISTORY".equals(item.payload().get("semanticDomain"))));
    assertTrue(semanticMemoryService.searchProject(
        snapshot.taskSchema().batch().projectKey(),
        "LocalCodexWorkerTransport",
        10,
        Map.of()
    ).stream().anyMatch(item -> "CODE_REPO".equals(item.payload().get("semanticDomain"))));
  }

  private Path initializeFixtureRepo(Path repoPath) throws Exception {
    Path source = repoPath.resolve(
        "src/main/java/com/agenttaskmanager/app/orchestration/LocalCodexWorkerTransport.java"
    );
    Files.createDirectories(source.getParent());
    Files.writeString(source, "final class LocalCodexWorkerTransport {}\n", StandardCharsets.UTF_8);
    Files.writeString(repoPath.resolve("AGENTS.md"), "# Fixture agents\n", StandardCharsets.UTF_8);
    Files.writeString(repoPath.resolve("ARCHITECTURE.md"), "# Fixture architecture\n", StandardCharsets.UTF_8);
    run(repoPath, "git", "init", "-b", "main");
    run(repoPath, "git", "config", "user.email", "integration@example.com");
    run(repoPath, "git", "config", "user.name", "Integration Test");
    run(repoPath, "git", "add", ".");
    run(repoPath, "git", "commit", "-m", "Initial fixture");
    Files.writeString(
        source,
        "final class LocalCodexWorkerTransport { String status() { return \"active\"; } }\n",
        StandardCharsets.UTF_8
    );
    run(repoPath, "git", "add", ".");
    run(repoPath, "git", "commit", "-m", "Update fixture");
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
