package com.agenttaskmanager.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.retrieval.SemanticCollectionDomain;
import com.agenttaskmanager.app.retrieval.SemanticContentType;
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
    sharedTaskContextService.deleteProjectSemanticContexts("local-test", Map.of());
    String storedId = sharedTaskContextService.storeProjectSemanticDocument(
        "local-test",
        "task-" + suffix,
        "worker-" + suffix,
        "architecture-note",
        "architecture-note",
        "Gemini fallback integration text " + suffix,
        SemanticCollectionDomain.KNOWLEDGE_RULES,
        SemanticContentType.DOCUMENTATION,
        Map.of("scope", "integration")
    ).getFirst();

    List<RetrievedSemanticContext> contexts = sharedTaskContextService.searchProjectRelatedContexts(
        "local-test",
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
    assertEquals("Gemini fallback integration text " + suffix, match.payload().get("chunkText"));
    assertEquals("KNOWLEDGE_RULES", match.payload().get("semanticDomain"));
  }
}
