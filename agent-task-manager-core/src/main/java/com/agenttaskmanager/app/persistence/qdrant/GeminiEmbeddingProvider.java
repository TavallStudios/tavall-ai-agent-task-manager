package com.agenttaskmanager.app.persistence.qdrant;

import com.agenttaskmanager.app.config.EmbeddingProperties;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.stereotype.Component;
import org.tavall.gemini.clients.GeminiEmbeddingClient;
import org.tavall.gemini.clients.response.GeminiEmbeddingResponse;

@Component
public class GeminiEmbeddingProvider implements EmbeddingProvider {

  private final EmbeddingProperties embeddingProperties;
  private GeminiEmbeddingClient client;

  public GeminiEmbeddingProvider(EmbeddingProperties embeddingProperties) {
    this.embeddingProperties = embeddingProperties;
  }

  @Override
  public String providerId() {
    return "gemini";
  }

  @Override
  public boolean isConfigured() {
    return embeddingProperties.getGeminiApiKey() != null && !embeddingProperties.getGeminiApiKey().isBlank();
  }

  @Override
  public EmbeddingVectorResult embed(String title, String text, EmbeddingPurpose purpose) {
    return embedText(title, text, purpose);
  }

  @PreDestroy
  public void closeClient() throws Exception {
    if (client != null) {
      client.close();
    }
  }

  private EmbeddingVectorResult embedText(String title, String text, EmbeddingPurpose embeddingPurpose) {
    if (!isConfigured()) {
      throw new IllegalStateException("Gemini embeddings are not configured.");
    }
    GeminiEmbeddingResponse response = buildClient().embed(
        embeddingProperties.getGeminiModel(),
        text == null ? "" : text,
        title,
        embeddingProperties.getDimensions(),
        embeddingPurpose.name()
    );
    List<Double> vector = normalize(response.vector());
    if (vector.size() != embeddingProperties.getDimensions()) {
      throw new IllegalStateException("Gemini embedding dimensions did not match the configured Qdrant vector size.");
    }
    return new EmbeddingVectorResult(providerId(), response.model(), vector);
  }

  private List<Double> normalize(List<Double> vector) {
    if (vector == null || vector.isEmpty() || embeddingProperties.getDimensions() == 3072) {
      return vector;
    }
    double magnitude = 0.0D;
    for (Double value : vector) {
      double component = value == null ? 0.0D : value;
      magnitude += component * component;
    }
    if (magnitude <= 0.0D) {
      return vector;
    }
    double divisor = Math.sqrt(magnitude);
    return vector.stream()
        .map(value -> (value == null ? 0.0D : value) / divisor)
        .toList();
  }

  private synchronized GeminiEmbeddingClient buildClient() {
    if (client != null) {
      return client;
    }
    client = new GeminiEmbeddingClient(embeddingProperties.getGeminiApiKey());
    return client;
  }
}
