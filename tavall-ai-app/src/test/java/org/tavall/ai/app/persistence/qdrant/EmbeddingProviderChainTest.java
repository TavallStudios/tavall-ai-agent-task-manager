package org.tavall.ai.app.persistence.qdrant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.tavall.ai.app.config.EmbeddingProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingProviderChainTest {

  @Test
  void shouldNotSilentlyAppendHashWhenOnlyLocalIsConfigured() {
    EmbeddingProperties properties = new EmbeddingProperties();
    properties.setProviderOrder(List.of("local"));
    ProviderFixture fixture = fixture(properties);
    when(fixture.local().isConfigured()).thenReturn(true);
    when(fixture.local().embed("title", "body", EmbeddingPurpose.RETRIEVAL_DOCUMENT))
        .thenThrow(new IllegalStateException("local embedding unavailable"));

    assertThrows(
        IllegalStateException.class,
        () -> fixture.chain().embedDocument("title", "body")
    );
    verify(fixture.hash(), never()).embed("title", "body", EmbeddingPurpose.RETRIEVAL_DOCUMENT);
  }

  @Test
  void shouldAllowHashOnlyWhenExplicitlyConfigured() {
    EmbeddingProperties properties = new EmbeddingProperties();
    properties.setProviderOrder(List.of("hash"));
    ProviderFixture fixture = fixture(properties);
    when(fixture.hash().isConfigured()).thenReturn(true);
    when(fixture.hash().embed("title", "body", EmbeddingPurpose.RETRIEVAL_DOCUMENT))
        .thenReturn(new EmbeddingVectorResult("hash", "hash", List.of(1.0D, 0.0D)));

    EmbeddingVectorResult result = fixture.chain().embedDocument("title", "body");

    assertEquals("hash", result.providerId());
  }

  private ProviderFixture fixture(EmbeddingProperties properties) {
    properties.setDimensions(2);
    GeminiEmbeddingProvider gemini = mock(GeminiEmbeddingProvider.class);
    LocalCommandEmbeddingProvider local = mock(LocalCommandEmbeddingProvider.class);
    HashEmbeddingService hash = mock(HashEmbeddingService.class);
    when(gemini.providerId()).thenReturn("gemini");
    when(local.providerId()).thenReturn("local");
    when(hash.providerId()).thenReturn("hash");
    return new ProviderFixture(
        new EmbeddingProviderChain(properties, gemini, local, hash),
        local,
        hash
    );
  }

  private record ProviderFixture(
      EmbeddingProviderChain chain,
      LocalCommandEmbeddingProvider local,
      HashEmbeddingService hash
  ) {
  }
}
