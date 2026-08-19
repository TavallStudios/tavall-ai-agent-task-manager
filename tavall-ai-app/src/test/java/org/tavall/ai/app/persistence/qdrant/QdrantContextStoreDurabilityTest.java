package org.tavall.ai.app.persistence.qdrant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.tavall.ai.app.config.QdrantProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QdrantContextStoreDurabilityTest {

  @Test
  void shouldUseInMemoryStoreOnlyWhenQdrantIsUnconfigured() {
    QdrantContextStore store = store(new QdrantProperties(), mock(HttpClient.class));

    assertFalse(store.isConfigured());
    assertTrue(store.isLocalFallbackEnabled());
  }

  @Test
  void shouldFailClosedInsteadOfWritingToMemoryWhenConfiguredQdrantIsUnavailable() throws Exception {
    QdrantProperties properties = new QdrantProperties();
    properties.setBaseUrl("http://qdrant.invalid");
    HttpClient httpClient = mock(HttpClient.class);
    doThrow(new IOException("offline"))
        .when(httpClient)
        .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

    EmbeddingProviderChain embeddingProviderChain = mock(EmbeddingProviderChain.class);
    when(embeddingProviderChain.embed(
        "test-kind",
        "durable memory",
        EmbeddingPurpose.RETRIEVAL_DOCUMENT
    )).thenReturn(new EmbeddingVectorResult("local", "BAAI/bge-small-en-v1.5", List.of(1.0D, 0.0D)));
    when(embeddingProviderChain.dimensions()).thenReturn(2);
    InMemoryQdrantStore inMemoryQdrantStore = mock(InMemoryQdrantStore.class);
    QdrantContextStore store = new QdrantContextStore(
        httpClient,
        embeddingProviderChain,
        inMemoryQdrantStore,
        new ObjectMapper(),
        properties
    );

    assertTrue(store.isConfigured());
    assertFalse(store.isLocalFallbackEnabled());
    assertThrows(
        IllegalStateException.class,
        () -> store.upsertContext("durable_collection", "point-1", "test-kind", "durable memory", Map.of())
    );
    verify(inMemoryQdrantStore, never()).upsert(any(), any(), any(), any());
  }

  private QdrantContextStore store(QdrantProperties properties, HttpClient httpClient) {
    return new QdrantContextStore(
        httpClient,
        mock(EmbeddingProviderChain.class),
        mock(InMemoryQdrantStore.class),
        new ObjectMapper(),
        properties
    );
  }
}
