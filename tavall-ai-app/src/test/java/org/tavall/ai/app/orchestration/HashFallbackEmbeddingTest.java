package org.tavall.ai.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.retrieval.SemanticCollectionDomain;
import org.tavall.ai.app.retrieval.SemanticContentType;
import org.tavall.ai.app.support.IntegrationTestSupport;
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
class HashFallbackEmbeddingTest extends IntegrationTestSupport {

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @Test
  void shouldUseHashEmbeddingWhenTheLocalRunnerFails() {
    String suffix = UUID.randomUUID().toString();
    sharedTaskContextService.deleteProjectSemanticContexts("hash-test", Map.of());
    String storedId = sharedTaskContextService.storeProjectSemanticDocument(
        "hash-test",
        "task-" + suffix,
        "worker-" + suffix,
        "diff-review",
        "diff-review",
        "Hash fallback integration text " + suffix,
        SemanticCollectionDomain.CODE_REPO,
        SemanticContentType.DIFF,
        Map.of("scope", "integration")
    ).getFirst();

    List<RetrievedSemanticContext> contexts = sharedTaskContextService.searchProjectRelatedContexts(
        "hash-test",
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
    assertEquals("Hash fallback integration text " + suffix, match.payload().get("chunkText"));
    assertEquals("CODE_REPO", match.payload().get("semanticDomain"));
  }
}

