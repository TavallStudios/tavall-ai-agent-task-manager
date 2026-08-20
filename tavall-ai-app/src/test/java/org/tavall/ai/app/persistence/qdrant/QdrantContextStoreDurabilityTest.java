package org.tavall.ai.app.persistence.qdrant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tavall.ai.app.config.EmbeddingProperties;
import org.tavall.ai.app.config.QdrantProperties;

class QdrantContextStoreDurabilityTest {

  @Test
  void shouldUseInMemoryStoreOnlyWhenQdrantIsUnconfigured() {
    QdrantProperties properties = new QdrantProperties();
    QdrantContextStore store = store(properties, chain(2), new InMemoryQdrantStore());

    assertFalse(store.isConfigured());
    assertTrue(store.isLocalFallbackEnabled());
  }

  @Test
  void shouldFailClosedInsteadOfWritingToMemoryWhenConfiguredQdrantIsUnavailable() {
    QdrantProperties properties = new QdrantProperties();
    properties.setBaseUrl("http://127.0.0.1:1");
    InMemoryQdrantStore memory = new InMemoryQdrantStore();
    QdrantContextStore store = store(properties, chain(2), memory);

    assertTrue(store.isConfigured());
    assertFalse(store.isLocalFallbackEnabled());
    assertThrows(
        IllegalStateException.class,
        () -> store.upsertContext("durable_collection", "point-1", "test-kind", "durable memory", Map.of())
    );
    assertTrue(memory.search("durable_collection", List.of(1.0D, 0.0D), 5, Map.of()).isEmpty());
  }

  @Test
  void shouldVerifySchemaForwardApiKeyAndRoundTripDurableContext() throws Exception {
    try (QdrantTestServer server = new QdrantTestServer()) {
      QdrantProperties properties = new QdrantProperties();
      properties.setBaseUrl(server.baseUrl());
      properties.setApiKey("test-key");
      server.setExpectedApiKey("test-key");
      QdrantContextStore store = store(properties, chain(2), new InMemoryQdrantStore());

      store.upsertContext("durable_collection", "point-1", "test-kind", "durable memory", Map.of("source", "test"));
      server.setQueryResponse("{\"status\":\"ok\",\"result\":[{\"id\":\"point-1\",\"score\":0.9,\"payload\":{\"source\":\"test\"}}]}");
      var contexts = store.searchRelatedContexts("durable_collection", "durable memory", 5);

      assertEquals("point-1", contexts.getFirst().id());
      assertEquals("test", contexts.getFirst().payload().get("source"));
      assertTrue(server.requests().contains("GET /collections/durable_collection"));
      assertTrue(server.requests().contains("POST /collections/durable_collection/points/query"));
      assertTrue(store.hasSuccessfulRequest());
    }
  }

  @Test
  void shouldRejectAnIncompatibleExistingCollectionSchema() throws Exception {
    try (QdrantTestServer server = new QdrantTestServer()) {
      server.setSchemaSize(3);
      QdrantProperties properties = new QdrantProperties();
      properties.setBaseUrl(server.baseUrl());

      assertThrows(
          IllegalStateException.class,
          () -> store(properties, chain(2), new InMemoryQdrantStore())
              .upsertContext("wrong_schema", "point-1", "kind", "body", Map.of())
      );
    }
  }

  @Test
  void shouldRejectMalformedQueryPointsInsteadOfReturningFakeEmptyContext() throws Exception {
    try (QdrantTestServer server = new QdrantTestServer()) {
      server.setQueryResponse("{\"status\":\"ok\",\"result\":[{\"id\":\"point-1\",\"score\":\"bad\",\"payload\":{}}]}");
      QdrantProperties properties = new QdrantProperties();
      properties.setBaseUrl(server.baseUrl());
      QdrantContextStore store = store(properties, chain(2), new InMemoryQdrantStore());

      assertThrows(
          IllegalStateException.class,
          () -> store.searchRelatedContexts("malformed", "query", 5)
      );
    }
  }

  @Test
  void shouldReturnNoResultsForEmptyQueriesAndBoundSearchSize() throws Exception {
    try (QdrantTestServer server = new QdrantTestServer()) {
      QdrantProperties properties = new QdrantProperties();
      properties.setBaseUrl(server.baseUrl());
      QdrantContextStore store = store(properties, chain(2), new InMemoryQdrantStore());

      assertTrue(store.searchRelatedContexts("bounded", "   ", 500).isEmpty());
      assertTrue(server.requests().isEmpty());

      server.setQueryResponse("{\"status\":\"ok\",\"result\":[]}");
      assertTrue(store.searchRelatedContexts("bounded", "q".repeat(20_000), 500).isEmpty());
      assertTrue(server.requests().contains("POST /collections/bounded/points/query"));
    }
  }

  private QdrantContextStore store(
      QdrantProperties properties,
      EmbeddingProviderChain chain,
      InMemoryQdrantStore memory
  ) {
    return new QdrantContextStore(
        HttpClient.newHttpClient(),
        chain,
        memory,
        new ObjectMapper(),
        properties
    );
  }

  private EmbeddingProviderChain chain(int dimensions) {
    EmbeddingProperties properties = new EmbeddingProperties();
    properties.setProviderOrder(List.of("hash"));
    properties.setDimensions(dimensions);
    ObjectMapper objectMapper = new ObjectMapper();
    return new EmbeddingProviderChain(
        properties,
        new GeminiEmbeddingProvider(properties, objectMapper),
        new LocalCommandEmbeddingProvider(properties, objectMapper),
        new HashEmbeddingService(properties)
    );
  }
}
