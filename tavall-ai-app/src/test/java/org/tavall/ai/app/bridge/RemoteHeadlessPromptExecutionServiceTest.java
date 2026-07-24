package org.tavall.ai.app.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.model.PromptRequestDetail;
import org.tavall.ai.app.model.PromptRequestSummary;
import org.tavall.ai.app.orchestration.CodexRunResult;
import org.tavall.ai.app.persistence.postgres.PromptRequestRepository;
import org.tavall.ai.app.service.PromptRequestService;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class RemoteHeadlessPromptExecutionServiceTest extends IntegrationTestSupport {

  @Autowired
  private PromptRequestRepository promptRequestRepository;

  @Autowired
  private PromptRequestService promptRequestService;

  @Autowired
  private RemoteHeadlessPromptExecutionService remoteHeadlessPromptExecutionService;

  @Autowired
  private JdbcClient jdbcClient;

  @BeforeEach
  void cleanupPromptQueue() {
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_messages").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_runs").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_requests").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_threads").update();
  }

  @Test
  void shouldFailRemoteHeadlessPromptRunsThatSkipGitWorkflow(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-bridge"));
    PromptRequestSummary summary = promptRequestService.create(
        "prompt-bridge-test",
        repoPath.toString(),
        RemoteHeadlessPromptExecutionService.BRIDGE_TARGET,
        "edit",
        "[skip-git-workflow] Update the repository",
        "integration-test",
        "integration-suite"
    );

    CodexRunResult result = remoteHeadlessPromptExecutionService.executeClaim(
        promptRequestRepository.claimNextQueued("test-bridge-agent", RemoteHeadlessPromptExecutionService.BRIDGE_TARGET).orElseThrow()
    );
    PromptRequestDetail detail = promptRequestService.getDetail(summary.requestId());

    assertEquals(97, result.effectiveExitCode());
    assertFalse(result.toolPolicyAudit().passed());
    assertEquals("failed", detail.request().status());
    assertEquals("failed", detail.runs().getFirst().status());
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-memory-lookup".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-harness-bootstrap".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-java-symbol-context".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-tool-policy".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-semantic-sync".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-git-workflow".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "codex-tool-call".equals(message.messageKind())));
    assertTrue(detail.messages().stream()
        .filter(message -> "codex-tool-call".equals(message.messageKind()))
        .allMatch(message -> message.body().contains("Observed tool call:")));
    assertTrue(detail.runs().getFirst().summary().contains("Tool policy gate failed"));
  }

  @Test
  void shouldReportCommitOutcomeForSuccessfulRemoteHeadlessPromptRuns(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-bridge-success"));
    PromptRequestSummary summary = promptRequestService.create(
        "prompt-bridge-success",
        repoPath.toString(),
        RemoteHeadlessPromptExecutionService.BRIDGE_TARGET,
        "edit",
        "Apply the repository update",
        "integration-test",
        "integration-suite"
    );

    CodexRunResult result = remoteHeadlessPromptExecutionService.executeClaim(
        promptRequestRepository.claimNextQueued("test-bridge-agent", RemoteHeadlessPromptExecutionService.BRIDGE_TARGET).orElseThrow()
    );
    PromptRequestDetail detail = promptRequestService.getDetail(summary.requestId());

    assertEquals(0, result.effectiveExitCode());
    assertTrue(result.toolPolicyAudit().commitCreated());
    assertEquals(1, result.toolPolicyAudit().commitCount());
    assertEquals("completed", detail.request().status());
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-harness-bootstrap".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-tool-policy".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-semantic-sync".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-git-workflow".equals(message.messageKind())));
    assertTrue(detail.runs().getFirst().summary().contains("Git workflow created 1 commit(s)"));
  }

  @Test
  void shouldFailRemoteHeadlessPromptRunsWhenJavaContractDeltaIsRisky(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-bridge-java-contract"));
    PromptRequestSummary summary = promptRequestService.create(
        "prompt-bridge-java-contract",
        repoPath.toString(),
        RemoteHeadlessPromptExecutionService.BRIDGE_TARGET,
        "edit",
        "[java-signature-change] Update the repository",
        "integration-test",
        "integration-suite"
    );

    CodexRunResult result = remoteHeadlessPromptExecutionService.executeClaim(
        promptRequestRepository.claimNextQueued("test-bridge-agent", RemoteHeadlessPromptExecutionService.BRIDGE_TARGET).orElseThrow()
    );
    PromptRequestDetail detail = promptRequestService.getDetail(summary.requestId());
    String failureMetadata = jdbcClient.sql("""
            SELECT metadata::text
            FROM agent_task_manager.prompt_messages
            WHERE request_id = :requestId
              AND message_kind = 'bridge-run-failure'
            ORDER BY created_at DESC, message_id DESC
            LIMIT 1
            """)
        .param("requestId", summary.requestId())
        .query(String.class)
        .single();

    assertEquals(98, result.effectiveExitCode());
    assertEquals("failed", detail.request().status());
    assertEquals("failed", detail.runs().getFirst().status());
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-harness-bootstrap".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-java-symbol-context".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-tool-policy".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-semantic-sync".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "bridge-git-workflow".equals(message.messageKind())));
    assertTrue(detail.runs().getFirst().summary().contains("Java symbol gate failed"));
    assertTrue(failureMetadata.contains("\"contractDeltaStatus\": \"failed\"")
        || failureMetadata.contains("\"contractDeltaStatus\":\"failed\""));
  }

  private Path initializeFixtureRepo(Path repoPath) throws Exception {
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(repoPath.resolve("README.md"), "# Prompt Bridge Fixture\n", StandardCharsets.UTF_8);
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
