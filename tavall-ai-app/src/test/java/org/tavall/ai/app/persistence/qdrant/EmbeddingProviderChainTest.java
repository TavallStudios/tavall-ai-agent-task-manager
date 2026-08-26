package org.tavall.ai.app.persistence.qdrant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tavall.ai.app.config.EmbeddingProperties;

class EmbeddingProviderChainTest {

  @Test
  void shouldNotSilentlyAppendHashWhenOnlyLocalIsConfigured() {
    EmbeddingProperties properties = properties("local");
    properties.setLocalCommand("/bin/false");
    EmbeddingProviderChain chain = chain(properties);

    assertThrows(
        IllegalStateException.class,
        () -> chain.embedDocument("title", "body")
    );
  }

  @Test
  void shouldAllowHashOnlyWhenExplicitlyConfigured() {
    EmbeddingProviderChain chain = chain(properties("hash"));

    EmbeddingVectorResult result = chain.embedDocument("title", "body");

    assertEquals("hash", result.providerId());
    assertEquals(2, result.vector().size());
  }

  @Test
  void shouldNormalizeProviderIdsBeforeResolvingTheConfiguredChain() {
    EmbeddingProviderChain chain = chain(properties(" HASH "));

    assertEquals("hash", chain.embedQuery("body").providerId());
  }

  @Test
  void shouldRejectUnknownProvidersInsteadOfSilentlyDroppingConfiguration() {
    assertThrows(IllegalArgumentException.class, () -> chain(properties("unknown")));
  }

  private EmbeddingProperties properties(String provider) {
    EmbeddingProperties properties = new EmbeddingProperties();
    properties.setProviderOrder(List.of(provider));
    properties.setDimensions(2);
    return properties;
  }

  private EmbeddingProviderChain chain(EmbeddingProperties properties) {
    return new EmbeddingProviderChain(
        properties,
        new GeminiEmbeddingProvider(properties, new ObjectMapper()),
        new LocalCommandEmbeddingProvider(properties, new ObjectMapper()),
        new HashEmbeddingService(properties)
    );
  }
}
