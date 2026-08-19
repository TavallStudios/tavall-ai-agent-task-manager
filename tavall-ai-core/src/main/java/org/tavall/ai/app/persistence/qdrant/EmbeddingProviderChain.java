package org.tavall.ai.app.persistence.qdrant;

import org.tavall.ai.app.config.EmbeddingProperties;
import org.tavall.ai.app.console.Log;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingProviderChain {

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
    return embed(title, text, EmbeddingPurpose.RETRIEVAL_DOCUMENT);
  }

  public EmbeddingVectorResult embedQuery(String text) {
    return embed(null, text, EmbeddingPurpose.RETRIEVAL_QUERY);
  }

  public EmbeddingVectorResult embedCodeQuery(String text) {
    return embed(null, text, EmbeddingPurpose.CODE_RETRIEVAL_QUERY);
  }

  public EmbeddingVectorResult embed(String title, String text, EmbeddingPurpose purpose) {
    return embedWithFallback(provider -> provider.embed(title, text, purpose));
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
          return normalize(result);
        }
        throw new IllegalStateException("Embedding provider returned a vector with the wrong size.");
      } catch (RuntimeException exception) {
        Log.warn("Embedding provider {} failed. Falling back to the next configured provider: {}", provider.providerId(), exception.getMessage());
        Log.exception(exception);
        failures.add(provider.providerId() + ": " + exception.getMessage());
      }
    }
    throw new IllegalStateException("No configured embedding provider succeeded. Failures: " + String.join(" | ", failures));
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
    return ordered;
  }

  private EmbeddingVectorResult normalize(EmbeddingVectorResult result) {
    if (dimensions == 3072) {
      return result;
    }
    double magnitude = Math.sqrt(result.vector().stream().mapToDouble(value -> value * value).sum());
    if (magnitude == 0.0D) {
      return result;
    }
    List<Double> normalized = result.vector().stream()
        .map(value -> value / magnitude)
        .toList();
    return new EmbeddingVectorResult(result.providerId(), result.modelName(), normalized);
  }

  @FunctionalInterface
  private interface EmbeddingOperation {
    EmbeddingVectorResult run(EmbeddingProvider provider);
  }
}
