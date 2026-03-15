package com.agenttaskmanager.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.qdrant.collection=agent_task_manager_context_embedding_local_test",
    "app.embedding.provider-order=gemini,local,hash",
    "app.embedding.dimensions=8",
    "app.embedding.gemini-api-key=",
    "app.embedding.local-command=${user.dir}/src/test/resources/bin/fake-embedding-runner",
    "app.embedding.local-model=fake-local-model"
})
class LocalFallbackEmbeddingIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @Test
  void shouldUseLocalEmbeddingRunnerWhenGeminiIsUnavailable() {
    String suffix = UUID.randomUUID().toString();
    String storedId = sharedTaskContextService.storeTaskEmbedding(
        "task-" + suffix,
        "worker-" + suffix,
        "architecture-note",
        "Gemini fallback integration text " + suffix,
        Map.of("scope", "integration")
    );

    List<RetrievedSemanticContext> contexts = sharedTaskContextService.searchRelatedContexts(
        "Gemini fallback integration text " + suffix,
        5
    );

    assertFalse(contexts.isEmpty());
    RetrievedSemanticContext match = contexts.stream()
        .filter(context -> storedId.equals(context.id()))
        .findFirst()
        .orElseThrow();
    assertNotNull(match.payload());
    assertEquals("local", match.payload().get("embeddingProvider"));
    assertEquals("fake-local-model", match.payload().get("embeddingModel"));
  }
}
