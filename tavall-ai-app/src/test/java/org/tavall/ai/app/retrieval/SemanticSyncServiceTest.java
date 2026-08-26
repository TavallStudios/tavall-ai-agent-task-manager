package org.tavall.ai.app.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.support.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class SemanticSyncServiceTest extends IntegrationTestSupport {

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private SemanticSyncService semanticSyncService;

  @Autowired
  private SemanticMemoryService semanticMemoryService;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.semantic_sync_outbox").update();
  }

  @Test
  void shouldEnqueueBackgroundOnlyProjectDocumentsUntilSyncLoopProcessesThem() {
    semanticMemoryService.enqueueProjectDocument(
        "background-project",
        new SemanticDocumentRequest(
            "background-doc",
            "",
            "",
            "background-test",
            "BackgroundDoc",
            "Background semantic indexing keeps this document out of Qdrant until the loop runs.",
            SemanticCollectionDomain.CODE_REPO,
            SemanticContentType.CODE,
            Map.of("sourcePath", "src/main/java/example/BackgroundDoc.java", "updatedAt", "2026-04-03T00:00:00Z")
        ),
        "background-doc"
    );

    List<org.tavall.ai.app.model.orchestration.RetrievedSemanticContext> before = semanticMemoryService.searchProject(
        "background-project",
        "Background semantic indexing keeps this document out of Qdrant",
        5,
        Map.of()
    );
    int processed = semanticSyncService.processPendingOperations();
    List<org.tavall.ai.app.model.orchestration.RetrievedSemanticContext> after = semanticMemoryService.searchProject(
        "background-project",
        "Background semantic indexing keeps this document out of Qdrant",
        5,
        Map.of()
    );

    assertTrue(before.isEmpty());
    assertEquals(1, processed);
    assertTrue(after.stream().anyMatch(item ->
        String.valueOf(item.payload().get("title")).contains("BackgroundDoc")
            && String.valueOf(item.payload().get("chunkText")).contains("Background semantic indexing")));
  }
}
