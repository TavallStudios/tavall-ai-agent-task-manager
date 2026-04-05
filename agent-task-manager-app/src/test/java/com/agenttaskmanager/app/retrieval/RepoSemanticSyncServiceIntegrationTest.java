package com.agenttaskmanager.app.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.model.KnownRepo;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.persistence.postgres.RepoSemanticSyncState;
import com.agenttaskmanager.app.persistence.postgres.RepoSemanticSyncStateRepository;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.service.RepoCatalogService;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.embedding.provider-order=hash",
    "app.embedding.dimensions=32",
    "app.memory-sync.managed-repo-backfill-enabled=true",
    "app.memory-sync.poll-interval-ms=3600000",
    "app.repo-catalog.max-depth=3"
})
class RepoSemanticSyncServiceIntegrationTest extends IntegrationTestSupport {

  private static final Path REPO_ROOT = Path.of(
      System.getProperty("java.io.tmpdir"),
      "agent-task-manager-memory-sync-it-" + System.nanoTime()
  );

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private RepoCatalogService repoCatalogService;

  @Autowired
  private RepoSemanticSyncService repoSemanticSyncService;

  @Autowired
  private RepoSemanticSyncStateRepository repoSemanticSyncStateRepository;

  @Autowired
  private SemanticSyncService semanticSyncService;

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("app.repo-catalog.roots", () -> REPO_ROOT.toString());
    registry.add("app.qdrant.project-collection-prefix", () -> "agent_task_manager_memory_sync_test");
  }

  @BeforeAll
  static void setUpRepos() throws Exception {
    createRepo(REPO_ROOT.resolve("MemorySyncRepo"));
  }

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.repo_semantic_sync_state").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.semantic_sync_outbox").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.shared_task_context").update();
  }

  @Test
  void shouldBackfillManagedReposIntoDurableSemanticSync() {
    int completed = repoSemanticSyncService.syncManagedRepos();
    semanticSyncService.processPendingOperations();
    KnownRepo repo = repoCatalogService.listRepos().getFirst();
    RepoSemanticSyncState state = repoSemanticSyncStateRepository.find(repo.projectKey()).orElseThrow();
    List<RetrievedSemanticContext> docs = sharedTaskContextService.searchProjectRelatedContexts(
        repo.projectKey(),
        "Use durable backfill memory",
        10
    );
    List<RetrievedSemanticContext> javaSymbols = sharedTaskContextService.searchProjectRelatedContexts(
        repo.projectKey(),
        "example.Example status",
        10,
        Map.of("javaSymbol", true)
    );
    Long pendingOperations = jdbcClient.sql("""
            SELECT count(*)
            FROM agent_task_manager.semantic_sync_outbox
            WHERE scope_key = :projectKey
            """)
        .param("projectKey", repo.projectKey())
        .query(Long.class)
        .single();

    assertEquals(1, completed);
    assertEquals(repo.repoPath(), state.repoPath());
    assertNotNull(state.lastSyncedAt());
    assertNotNull(state.lastScanStartedAt());
    assertNotNull(state.lastScanCompletedAt());
    assertFalse(state.lastSyncedHead().isBlank());
    assertTrue(state.lastError() == null || state.lastError().isBlank());
    assertTrue(docs.stream().anyMatch(item -> String.valueOf(item.payload().get("chunkText")).contains("Use durable backfill memory")));
    assertTrue(javaSymbols.stream().anyMatch(item ->
        "example.Example".equals(String.valueOf(item.payload().get("className")))));
    assertNotNull(pendingOperations);
    assertTrue(pendingOperations > 0);
  }

  @Test
  void shouldSyncJavaSymbolProfilesForWorkspaceJavaChanges() throws Exception {
    Path repoPath = REPO_ROOT.resolve("WorkspaceJavaSyncRepo-" + System.nanoTime());
    createRepo(repoPath);
    KnownRepo repo = repoCatalogService.requireByPath(repoPath.toString());

    Files.writeString(
        repoPath.resolve("src/main/java/example/Example.java"),
        """
        package example;

        public class Example {
          public String mode() {
            return "active";
          }
        }
        """,
        StandardCharsets.UTF_8
    );

    Map<String, Object> syncSummary = repoSemanticSyncService.syncWorkspaceChanges(repo, repoPath);
    semanticSyncService.processPendingOperations();

    List<RetrievedSemanticContext> javaSymbols = sharedTaskContextService.searchProjectRelatedContexts(
        repo.projectKey(),
        "example.Example mode",
        10,
        Map.of("javaSymbol", true)
    );

    assertEquals("completed", syncSummary.get("status"));
    assertTrue(((Number) syncSummary.get("upsertedJavaSymbols")).intValue() > 0);
    assertTrue(javaSymbols.stream().anyMatch(item ->
        "example.Example".equals(String.valueOf(item.payload().get("className")))
            && String.valueOf(item.payload().get("javaMethodNames")).contains("mode")));
  }

  @Test
  void shouldReconcileCommittedRepoChangesWithoutIndexingHarnessSidecars() throws Exception {
    Path repoPath = REPO_ROOT.resolve("FinalSyncRepo-" + System.nanoTime());
    createRepo(repoPath);
    KnownRepo repo = repoCatalogService.requireByPath(repoPath.toString());
    String baseRevision = runAndCapture(repoPath, "git", "rev-parse", "HEAD").strip();

    Files.writeString(
        repoPath.resolve("README.md"),
        "# Memory Sync Fixture\n\nFinal reconciliation captured this committed change.\n",
        StandardCharsets.UTF_8
    );
    run(repoPath, "git", "add", "README.md");
    run(repoPath, "git", "commit", "-m", "Update README");
    Files.writeString(
        repoPath.resolve(".agent-task-manager.last-message.txt"),
        "Harness sidecar output should not be indexed.\n",
        StandardCharsets.UTF_8
    );

    repoSemanticSyncService.reconcileWorkspaceChanges(repo, repoPath, baseRevision);

    List<RetrievedSemanticContext> readmeContexts = sharedTaskContextService.searchProjectRelatedContexts(
        repo.projectKey(),
        "Final reconciliation captured this committed change",
        10
    );
    List<RetrievedSemanticContext> sidecarContexts = sharedTaskContextService.searchProjectRelatedContexts(
        repo.projectKey(),
        "Harness sidecar output should not be indexed",
        10
    );

    assertTrue(readmeContexts.stream().anyMatch(item ->
        "README.md".equals(String.valueOf(item.payload().get("sourcePath")))
            && String.valueOf(item.payload().get("chunkText")).contains("Final reconciliation captured this committed change")));
    assertTrue(sidecarContexts.isEmpty() || sidecarContexts.stream().noneMatch(item ->
        ".agent-task-manager.last-message.txt".equals(String.valueOf(item.payload().get("sourcePath")))));
  }

  private static void createRepo(Path repoPath) throws Exception {
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(
        repoPath.resolve("README.md"),
        "# Memory Sync Fixture\n\nUse durable backfill memory.\n",
        StandardCharsets.UTF_8
    );
    Files.writeString(
        repoPath.resolve("src/main/java/example/Example.java"),
        """
        package example;

        public class Example {
          public String status() {
            return "ready";
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
  }
  private static void run(Path repoPath, String... command) throws Exception {
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

  private static String runAndCapture(Path repoPath, String... command) throws Exception {
    Process process = new ProcessBuilder(command)
        .directory(repoPath.toFile())
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();

    if (exitCode != 0) {
      throw new IOException(String.join(" ", command) + " failed: " + output);
    }
    return output;
  }
}
