package com.agenttaskmanager.app.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class SemanticSyncServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private SemanticSyncService semanticSyncService;

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.semantic_sync_outbox").update();
  }

  @Test
  void shouldEnqueueBackgroundOnlyProjectDocumentsUntilSyncLoopProcessesThem() {
    sharedTaskContextService.enqueueProjectSemanticDocument(
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

    List<com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext> before = sharedTaskContextService.searchProjectRelatedContexts(
        "background-project",
        "Background semantic indexing keeps this document out of Qdrant",
        5
    );
    int processed = semanticSyncService.processPendingOperations();
    List<com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext> after = sharedTaskContextService.searchProjectRelatedContexts(
        "background-project",
        "Background semantic indexing keeps this document out of Qdrant",
        5
    );

    assertTrue(before.isEmpty());
    assertEquals(1, processed);
    assertTrue(after.stream().anyMatch(item ->
        String.valueOf(item.payload().get("title")).contains("BackgroundDoc")
            && String.valueOf(item.payload().get("chunkText")).contains("Background semantic indexing")));
  }
}
