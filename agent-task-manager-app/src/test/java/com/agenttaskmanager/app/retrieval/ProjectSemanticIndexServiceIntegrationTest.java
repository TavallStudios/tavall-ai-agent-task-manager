package com.agenttaskmanager.app.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.embedding.provider-order=hash",
    "app.embedding.dimensions=32",
    "app.semantic-index.reindex-repo-names=AgentTaskManager,Portfolio",
    "app.repo-catalog.max-depth=3"
})
class ProjectSemanticIndexServiceIntegrationTest extends IntegrationTestSupport {

  private static final Path REPO_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "agent-task-manager-project-reindex-it");

  @Autowired
  private ProjectSemanticIndexService projectSemanticIndexService;

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("app.repo-catalog.roots", () -> REPO_ROOT.toString());
    registry.add("app.qdrant.project-collection-prefix", () -> "agent_task_manager_project_reindex_test");
  }

  @BeforeAll
  static void setUpRepos() throws Exception {
    deleteIfPresent(REPO_ROOT);
    createRepo(REPO_ROOT.resolve("AgentTaskManager"), "HarnessDoc.md", "# Rules\n\nUse chunked retrieval.\n", "Example.java");
    createRepo(REPO_ROOT.resolve("IgnoredRepo"), "README.md", "# Ignore\n", "Ignored.java");
  }

  @Test
  void shouldReindexOnlyConfiguredCodebases() {
    ProjectSemanticIndexService.ProjectSemanticIndexSummary summary = projectSemanticIndexService.reindexConfiguredRepos();

    assertEquals(1, summary.reposReindexed());
    assertEquals("AgentTaskManager", summary.repositories().get(0).displayName());
    assertTrue(summary.repositories().get(0).indexedJavaSymbols() >= 1);
    String projectKey = summary.repositories().get(0).projectKey();

    List<com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext> docs = sharedTaskContextService.searchProjectRelatedContexts(
        projectKey,
        "chunked retrieval",
        10
    );
    assertTrue(docs.stream().anyMatch(item -> "KNOWLEDGE_RULES".equals(item.payload().get("semanticDomain"))));
    assertTrue(docs.stream().anyMatch(item -> String.valueOf(item.payload().get("chunkText")).contains("chunked retrieval")));

    List<com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext> code = sharedTaskContextService.searchProjectRelatedContexts(
        projectKey,
        "find the code that returns ready",
        10
    );
    assertTrue(code.stream().anyMatch(item -> "CODE_REPO".equals(item.payload().get("semanticDomain"))));

    List<RetrievedSemanticContext> javaSymbols = sharedTaskContextService.searchProjectRelatedContexts(
        projectKey,
        "Example class status method java signature",
        10
    );
    assertTrue(Boolean.parseBoolean(String.valueOf(javaSymbols.getFirst().payload().get("javaSymbol"))));
    assertEquals("example.Example", javaSymbols.getFirst().payload().get("className"));
  }

  private static void createRepo(Path repoPath, String docName, String docBody, String javaName) throws IOException {
    Files.createDirectories(repoPath.resolve(".git"));
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(repoPath.resolve(docName), docBody, StandardCharsets.UTF_8);
    Files.writeString(
        repoPath.resolve("src/main/java/example/" + javaName),
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
  }

  private static void deleteIfPresent(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (var stream = Files.walk(path)) {
      stream.sorted(java.util.Comparator.reverseOrder())
          .forEach(candidate -> {
            try {
              Files.deleteIfExists(candidate);
            } catch (IOException exception) {
              throw new IllegalStateException(exception);
            }
          });
    }
  }
}
