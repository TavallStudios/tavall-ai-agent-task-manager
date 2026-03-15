package com.agenttaskmanager.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.qdrant.collection=agent_task_manager_context_embedding_hash_test",
    "app.embedding.provider-order=local,hash",
    "app.embedding.dimensions=8",
    "app.embedding.gemini-api-key=",
    "app.embedding.local-command=/bin/false",
    "app.embedding.local-model=fake-local-model"
})
class HashFallbackEmbeddingIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @Test
  void shouldUseHashEmbeddingWhenTheLocalRunnerFails() {
    String suffix = UUID.randomUUID().toString();
    String storedId = sharedTaskContextService.storeTaskEmbedding(
        "task-" + suffix,
        "worker-" + suffix,
        "diff-review",
        "Hash fallback integration text " + suffix,
        Map.of("scope", "integration")
    );

    List<RetrievedSemanticContext> contexts = sharedTaskContextService.searchRelatedContexts(
        "Hash fallback integration text " + suffix,
        5
    );

    assertFalse(contexts.isEmpty());
    RetrievedSemanticContext match = contexts.stream()
        .filter(context -> storedId.equals(context.id()))
        .findFirst()
        .orElseThrow();
    assertEquals("hash", match.payload().get("embeddingProvider"));
    assertEquals("hash-fallback-v1", match.payload().get("embeddingModel"));
  }
}
