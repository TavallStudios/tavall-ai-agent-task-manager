package org.tavall.ai.app.persistence.qdrant;

import org.tavall.ai.app.config.EmbeddingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

@Component
public class GeminiEmbeddingProvider implements EmbeddingProvider {

  private static final String GEMINI_API_ROOT = "https://generativelanguage.googleapis.com/v1beta/models/";

  private final EmbeddingProperties embeddingProperties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public GeminiEmbeddingProvider(EmbeddingProperties embeddingProperties, ObjectMapper objectMapper) {
    this.embeddingProperties = embeddingProperties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newHttpClient();
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

  private EmbeddingVectorResult embedText(String title, String text, EmbeddingPurpose embeddingPurpose) {
    if (!isConfigured()) {
      throw new IllegalStateException("Gemini embeddings are not configured.");
    }

    JsonNode response = invokeEmbeddingApi(title, text == null ? "" : text, embeddingPurpose);
    JsonNode values = response.path("embedding").path("values");
    if (!values.isArray()) {
      throw new IllegalStateException("Gemini embedding response did not include embedding.values.");
    }

    List<Double> vector = normalize(readVector(values));
    if (vector.size() != embeddingProperties.getDimensions()) {
      throw new IllegalStateException("Gemini embedding dimensions did not match the configured Qdrant vector size.");
    }
    return new EmbeddingVectorResult(providerId(), normalizeModelName(), vector);
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

  private JsonNode invokeEmbeddingApi(String title, String text, EmbeddingPurpose embeddingPurpose) {
    try {
      String requestBody = objectMapper.writeValueAsString(buildRequestPayload(title, text, embeddingPurpose));
      HttpRequest request = HttpRequest.newBuilder(buildEndpointUri())
          .header("Content-Type", "application/json")
          .header("x-goog-api-key", embeddingProperties.getGeminiApiKey())
          .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() / 100 == 2) {
        return objectMapper.readTree(response.body());
      }
      throw new IllegalStateException("Gemini embedding request failed with status "
          + response.statusCode()
          + ": "
          + response.body());
    } catch (IOException exception) {
      throw new IllegalStateException("Gemini embedding response could not be parsed.", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Gemini embedding request was interrupted.", exception);
    }
  }

  private Map<String, Object> buildRequestPayload(String title, String text, EmbeddingPurpose embeddingPurpose) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", "models/" + normalizeModelName());
    payload.put("content", Map.of("parts", List.of(Map.of("text", text))));
    payload.put("taskType", embeddingPurpose.name());
    payload.put("outputDimensionality", embeddingProperties.getDimensions());
    if (embeddingPurpose == EmbeddingPurpose.RETRIEVAL_DOCUMENT && title != null && !title.isBlank()) {
      payload.put("title", title);
    }
    return payload;
  }

  private URI buildEndpointUri() {
    return URI.create(GEMINI_API_ROOT + normalizeModelName() + ":embedContent");
  }

  private String normalizeModelName() {
    String configured = embeddingProperties.getGeminiModel();
    return configured.startsWith("models/") ? configured.substring("models/".length()) : configured;
  }

  private List<Double> readVector(JsonNode values) {
    return StreamSupport.stream(values.spliterator(), false)
        .map(JsonNode::asDouble)
        .toList();
  }
}

