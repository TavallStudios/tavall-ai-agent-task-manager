package com.agenttaskmanager.app.persistence.qdrant;

import com.agenttaskmanager.app.config.EmbeddingProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingProviderChain {

  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingProviderChain.class);

  private final int dimensions;
  private final List<EmbeddingProvider> orderedProviders;

  public EmbeddingProviderChain(
      EmbeddingProperties embeddingProperties,
      GeminiEmbeddingProvider geminiEmbeddingProvider,
      LocalCommandEmbeddingProvider localCommandEmbeddingProvider,
      HashEmbeddingService hashEmbeddingService
  ) {
    this.dimensions = embeddingProperties.getDimensions();
    this.orderedProviders = orderProviders(
        embeddingProperties,
        geminiEmbeddingProvider,
        localCommandEmbeddingProvider,
        hashEmbeddingService
    );
  }

  public EmbeddingVectorResult embedDocument(String title, String text) {
    return embedWithFallback(provider -> provider.embedDocument(title, text));
  }

  public EmbeddingVectorResult embedQuery(String text) {
    return embedWithFallback(provider -> provider.embedQuery(text));
  }

  public int dimensions() {
    return dimensions;
  }

  private EmbeddingVectorResult embedWithFallback(EmbeddingOperation embeddingOperation) {
    List<String> failures = new ArrayList<>();
    for (EmbeddingProvider provider : orderedProviders) {
      if (!provider.isConfigured()) {
        continue;
      }
      try {
        EmbeddingVectorResult result = embeddingOperation.run(provider);
        if (result.vector().size() == dimensions) {
          return result;
        }
        throw new IllegalStateException("Embedding provider returned a vector with the wrong size.");
      } catch (RuntimeException exception) {
        LOGGER.warn("Embedding provider {} failed. Falling back to the next provider.", provider.providerId(), exception);
        failures.add(provider.providerId() + ": " + exception.getMessage());
      }
    }
    throw new IllegalStateException("No embedding provider succeeded. Failures: " + String.join(" | ", failures));
  }

  private List<EmbeddingProvider> orderProviders(
      EmbeddingProperties embeddingProperties,
      GeminiEmbeddingProvider geminiEmbeddingProvider,
      LocalCommandEmbeddingProvider localCommandEmbeddingProvider,
      HashEmbeddingService hashEmbeddingService
  ) {
    Map<String, EmbeddingProvider> providers = new LinkedHashMap<>();
    providers.put(geminiEmbeddingProvider.providerId(), geminiEmbeddingProvider);
    providers.put(localCommandEmbeddingProvider.providerId(), localCommandEmbeddingProvider);
    providers.put(hashEmbeddingService.providerId(), hashEmbeddingService);

    List<EmbeddingProvider> ordered = new ArrayList<>();
    for (String providerId : embeddingProperties.getProviderOrder()) {
      EmbeddingProvider provider = providers.get(providerId);
      if (provider != null && !ordered.contains(provider)) {
        ordered.add(provider);
      }
    }
    if (!ordered.contains(hashEmbeddingService)) {
      ordered.add(hashEmbeddingService);
    }
    return ordered;
  }

  @FunctionalInterface
  private interface EmbeddingOperation {
    EmbeddingVectorResult run(EmbeddingProvider provider);
  }
}
