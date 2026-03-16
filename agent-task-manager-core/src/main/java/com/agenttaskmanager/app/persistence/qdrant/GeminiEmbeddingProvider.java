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
  public EmbeddingVectorResult embedDocument(String title, String text) {
    return embedText(title, text, EmbeddingPurpose.RETRIEVAL_DOCUMENT);
  }

  @Override
  public EmbeddingVectorResult embedQuery(String text) {
    return embedText(null, text, EmbeddingPurpose.RETRIEVAL_QUERY);
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
    List<Double> vector = response.vector();
    if (vector.size() != embeddingProperties.getDimensions()) {
      throw new IllegalStateException("Gemini embedding dimensions did not match the configured Qdrant vector size.");
    }
    return new EmbeddingVectorResult(providerId(), response.model(), vector);
  }

  private synchronized GeminiEmbeddingClient buildClient() {
    if (client != null) {
      return client;
    }
    client = new GeminiEmbeddingClient(embeddingProperties.getGeminiApiKey());
    return client;
  }
}
