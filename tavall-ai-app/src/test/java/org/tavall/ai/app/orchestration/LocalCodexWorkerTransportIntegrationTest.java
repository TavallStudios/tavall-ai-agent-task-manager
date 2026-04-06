package org.tavall.ai.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.model.KnownRepo;
import org.tavall.ai.app.model.PromptThreadDetail;
import org.tavall.ai.app.model.PromptThreadMemoryLookupResult;
import org.tavall.ai.app.model.orchestration.OverseerTaskBatch;
import org.tavall.ai.app.model.orchestration.TaskAssignment;
import org.tavall.ai.app.model.orchestration.WorkerExecutionRequest;
import org.tavall.ai.app.model.orchestration.WorkerExecutionResult;
import org.tavall.ai.app.model.orchestration.WorkerTransportKind;
import org.tavall.ai.app.persistence.postgres.PromptThreadRepository;
import org.tavall.ai.app.service.PromptThreadMemoryService;
import org.tavall.ai.app.service.RepoCatalogService;
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

class LocalCodexWorkerTransportIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private LocalCodexWorkerTransport localCodexWorkerTransport;

  @Autowired
  private OverseerOrchestrationService overseerOrchestrationService;

  @Autowired
  private PromptThreadMemoryService promptThreadMemoryService;

  @Autowired
  private PromptThreadRepository promptThreadRepository;

  @Autowired
  private RepoCatalogService repoCatalogService;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_messages").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_runs").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_requests").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_threads").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.repo_semantic_sync_state").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.semantic_sync_outbox").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.shared_task_context").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.validation_violations").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.validation_reports").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.patch_decisions").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.cleanup_reviews").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.task_artifacts").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_checkins").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_task_leases").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_tasks").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.overseer_decisions").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.agent_tasks WHERE task_kind = 'orchestration-batch'").update();
  }

  @Test
  void shouldPersistWorkerTranscriptAndSyncRepoMemory(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    OverseerTaskBatch batch = overseerOrchestrationService.createTaskBatch(
        "worker-memory-test",
        repoPath.toString(),
        "Worker memory batch",
        true,
        List.of("implementation")
    );
    TaskAssignment assignment = overseerOrchestrationService.assignNextWorkerTask(
        batch.taskId(),
        "integration-worker",
        WorkerTransportKind.LOCAL_CODEX_EXEC,
        "session-worker"
    );

    WorkerExecutionResult result = localCodexWorkerTransport.executeWorkerTask(
        new WorkerExecutionRequest(
            batch.taskId(),
            assignment.workerTaskId(),
            "integration-worker",
            "session-worker",
            repoPath
        )
    );

    KnownRepo repo = repoCatalogService.requireByPath(repoPath.toString());
    String threadKey = "worker-task:" + assignment.workerTaskId();
    PromptThreadDetail detail = promptThreadRepository.getDetail(threadKey);

    assertEquals(0, result.exitCode());
    assertTrue(result.runSummary().summary().contains("Git workflow created 1 commit(s)"));
    assertEquals("local-codex-worker", detail.thread().bridgeTarget());
    assertEquals("fake-thread", detail.thread().threadSessionId());
    assertEquals(1, detail.requests().size());
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-harness-bootstrap".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-memory-lookup".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-java-symbol-context".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-tool-policy".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "codex-thread-started".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "codex-agent-message".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "codex-usage".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-semantic-sync".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-git-workflow".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-final-response".equals(message.messageKind())));
    assertTrue(detail.messages().stream()
        .filter(message -> "codex-tool-call".equals(message.messageKind()))
        .count() >= 5);
    assertTrue(detail.messages().stream()
        .filter(message -> "codex-tool-call".equals(message.messageKind()))
        .allMatch(message -> message.body().contains("Observed tool call:")));

    PromptThreadMemoryLookupResult transcriptLookup = promptThreadMemoryService.lookup(
        repo.projectKey(),
        threadKey,
        "fake-codex processed prompt"
    );
    Long repoOutboxCount = jdbcClient.sql("""
            SELECT count(*)
            FROM agent_task_manager.semantic_sync_outbox
            WHERE scope_key = :projectKey
              AND title = 'README.md'
            """)
        .param("projectKey", repo.projectKey())
        .query(Long.class)
        .single();
    Long tempArtifactOutboxCount = jdbcClient.sql("""
            SELECT count(*)
            FROM agent_task_manager.semantic_sync_outbox
            WHERE scope_key = :projectKey
              AND title = '.tavall-ai.last-message.txt'
            """)
        .param("projectKey", repo.projectKey())
        .query(Long.class)
        .single();

    assertTrue(transcriptLookup.summary().contains("Memory lookup completed."));
    assertNotNull(repoOutboxCount);
    assertTrue(repoOutboxCount >= 1);
    assertNotNull(tempArtifactOutboxCount);
    assertEquals(0L, tempArtifactOutboxCount);
    assertFalse(detail.messages().isEmpty());
  }

  @Test
  void shouldMarkWorkerNeedsReworkWhenJavaContractDeltaIsRisky(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-java-contract"));
    OverseerTaskBatch batch = overseerOrchestrationService.createTaskBatch(
        "worker-java-contract-test",
        repoPath.toString(),
        "[java-signature-change] Worker contract batch",
        true,
        List.of("implementation")
    );
    TaskAssignment assignment = overseerOrchestrationService.assignNextWorkerTask(
        batch.taskId(),
        "integration-worker",
        WorkerTransportKind.LOCAL_CODEX_EXEC,
        "session-worker"
    );

    WorkerExecutionResult result = localCodexWorkerTransport.executeWorkerTask(
        new WorkerExecutionRequest(
            batch.taskId(),
            assignment.workerTaskId(),
            "integration-worker",
            "session-worker",
            repoPath
        )
    );
    PromptThreadDetail detail = promptThreadRepository.getDetail("worker-task:" + assignment.workerTaskId());
    String outputMetadata = jdbcClient.sql("""
            SELECT metadata::text
            FROM agent_task_manager.task_artifacts
            WHERE artifact_id = :artifactId
            """)
        .param("artifactId", result.outputArtifactId())
        .query(String.class)
        .single();
    Long contractDeltaArtifacts = jdbcClient.sql("""
            SELECT count(*)
            FROM agent_task_manager.task_artifacts
            WHERE task_id = :taskId
              AND worker_task_id = :workerTaskId
              AND artifact_kind = 'java-contract-delta'
            """)
        .param("taskId", batch.taskId())
        .param("workerTaskId", assignment.workerTaskId())
        .query(Long.class)
        .single();

    assertEquals(0, result.exitCode());
    assertEquals("NEEDS_REWORK", result.runSummary().status());
    assertTrue(result.runSummary().summary().contains("java contract delta requires rework"));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-harness-bootstrap".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-java-symbol-context".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-tool-policy".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-semantic-sync".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "worker-git-workflow".equals(message.messageKind())));
    assertTrue(outputMetadata.contains("\"contractDeltaStatus\": \"failed\"")
        || outputMetadata.contains("\"contractDeltaStatus\":\"failed\""));
    assertNotNull(contractDeltaArtifacts);
    assertTrue(contractDeltaArtifacts >= 1);
  }

  private Path initializeFixtureRepo(Path repoPath) throws Exception {
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(
        repoPath.resolve("README.md"),
        "# Worker Fixture\n",
        StandardCharsets.UTF_8
    );
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        """
        package example;

        public class FixtureApp {

          public String greet(String name) {
            String message = "hello " + name;
            return message;
          }
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


