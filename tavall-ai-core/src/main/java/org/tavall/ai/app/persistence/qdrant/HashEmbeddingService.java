package org.tavall.ai.app.persistence.qdrant;

import org.tavall.ai.app.config.EmbeddingProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HashEmbeddingService implements EmbeddingProvider {

  private final EmbeddingProperties embeddingProperties;

  public HashEmbeddingService(EmbeddingProperties embeddingProperties) {
    this.embeddingProperties = embeddingProperties;
  }

  @Override
  public String providerId() {
    return "hash";
  }

  @Override
  public boolean isConfigured() {
    return true;
  }

  @Override
  public EmbeddingVectorResult embed(String title, String text, EmbeddingPurpose purpose) {
    String joined = purpose == EmbeddingPurpose.RETRIEVAL_DOCUMENT ? joinText(title, text) : text;
    return new EmbeddingVectorResult(providerId(), "hash-fallback-v1", embedText(joined));
  }

  private List<Double> embedText(String text) {
    List<Double> vector = new ArrayList<>(embeddingProperties.getDimensions());
    for (int index = 0; index < embeddingProperties.getDimensions(); index++) {
      vector.add(0.0D);
    }

    if (text == null || text.isBlank()) {
      return vector;
    }

    String normalized = text.strip().toLowerCase();
    for (int index = 0; index < normalized.length(); index++) {
      int bucket = index % embeddingProperties.getDimensions();
      char character = normalized.charAt(index);
      double next = vector.get(bucket) + (character / 255.0D);
      vector.set(bucket, next);
    }

    double magnitude = Math.sqrt(vector.stream().mapToDouble(value -> value * value).sum());
    if (magnitude == 0.0D) {
      return vector;
    }

    for (int index = 0; index < vector.size(); index++) {
      vector.set(index, vector.get(index) / magnitude);
    }
    return vector;
  }

  private String joinText(String title, String text) {
    if (title != null && !title.isBlank()) {
      return title + "\n" + (text == null ? "" : text);
    }
    return text == null ? "" : text;
  }
}

