package com.agenttaskmanager.app.persistence.qdrant;

import com.agenttaskmanager.app.config.QdrantProperties;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class QdrantContextStore {

  private final HttpClient httpClient;
  private final EmbeddingProviderChain embeddingProviderChain;
  private final ObjectMapper objectMapper;
  private final QdrantProperties qdrantProperties;

  public QdrantContextStore(
      HttpClient httpClient,
      EmbeddingProviderChain embeddingProviderChain,
      ObjectMapper objectMapper,
      QdrantProperties qdrantProperties
  ) {
    this.httpClient = httpClient;
    this.embeddingProviderChain = embeddingProviderChain;
    this.objectMapper = objectMapper;
    this.qdrantProperties = qdrantProperties;
  }

  public String storeContext(
      String collectionName,
      String taskId,
      String workerTaskId,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    String pointId = UUID.randomUUID().toString();
    Map<String, Object> fullPayload = new LinkedHashMap<>();
    fullPayload.put("taskId", taskId);
    if (workerTaskId != null && !workerTaskId.isBlank()) {
      fullPayload.put("workerTaskId", workerTaskId);
    }
    if (payload != null) {
      fullPayload.putAll(payload);
    }
    upsertContext(collectionName, pointId, kind, body, fullPayload);
    return pointId;
  }

  public String storeContext(
      String taskId,
      String workerTaskId,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    return storeContext(qdrantProperties.getCollection(), taskId, workerTaskId, kind, body, payload);
  }

  public String upsertContext(String collectionName, String pointId, String kind, String body, Map<String, Object> payload) {
    ensureCollection(collectionName);
    EmbeddingVectorResult embedding = embeddingProviderChain.embedDocument(kind, body);
    Map<String, Object> fullPayload = new LinkedHashMap<>();
    if (payload != null) {
      fullPayload.putAll(payload);
    }
    fullPayload.put("kind", kind);
    fullPayload.put("body", body);
    fullPayload.put("embeddingProvider", embedding.providerId());
    fullPayload.put("embeddingModel", embedding.modelName());
    fullPayload.put("embeddingDimensions", embedding.vector().size());

    Map<String, Object> point = Map.of(
        "id", pointId,
        "vector", embedding.vector(),
        "payload", fullPayload
    );
    sendRequest(
        "/collections/" + collectionName + "/points?wait=true",
        Map.of("points", List.of(point)),
        "PUT"
    );
    return pointId;
  }

  public String upsertContext(String pointId, String kind, String body, Map<String, Object> payload) {
    return upsertContext(qdrantProperties.getCollection(), pointId, kind, body, payload);
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(String collectionName, String queryText, int limit) {
    return searchRelatedContexts(collectionName, queryText, limit, Map.of());
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(String collectionName, String queryText, int limit, Map<String, Object> payloadFilter) {
    ensureCollection(collectionName);
    EmbeddingVectorResult embedding = embeddingProviderChain.embedQuery(queryText);
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("query", embedding.vector());
    requestBody.put("limit", limit);
    requestBody.put("with_payload", true);
    if (payloadFilter != null && !payloadFilter.isEmpty()) {
      requestBody.put("filter", buildFilter(payloadFilter));
    }
    Map<String, Object> response = sendRequest(
        "/collections/" + collectionName + "/points/query",
        requestBody
    );
    Object result = response.get("result");
    Object points = result;
    if (result instanceof Map<?, ?> resultMap) {
      points = resultMap.get("points");
    }
    if (!(points instanceof List<?> items)) {
      return List.of();
    }
    return items.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(item -> new RetrievedSemanticContext(
            String.valueOf(item.get("id")),
            ((Number) item.getOrDefault("score", 0.0D)).doubleValue(),
            (Map<String, Object>) item.getOrDefault("payload", Map.of())
        ))
        .toList();
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(String queryText, int limit) {
    return searchRelatedContexts(qdrantProperties.getCollection(), queryText, limit, Map.of());
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(String queryText, int limit, Map<String, Object> payloadFilter) {
    return searchRelatedContexts(qdrantProperties.getCollection(), queryText, limit, payloadFilter);
  }

  public void deleteContexts(String collectionName, Map<String, Object> payloadFilter) {
    if (payloadFilter == null || payloadFilter.isEmpty()) {
      return;
    }
    ensureCollection(collectionName);
    sendRequest(
        "/collections/" + collectionName + "/points/delete?wait=true",
        Map.of("filter", buildFilter(payloadFilter))
    );
  }

  public void deleteContexts(Map<String, Object> payloadFilter) {
    deleteContexts(qdrantProperties.getCollection(), payloadFilter);
  }

  public void deleteCollection(String collectionName) {
    sendRequest("/collections/" + collectionName, Map.of(), "DELETE");
  }

  private void ensureCollection(String collectionName) {
    sendRequest(
        "/collections/" + collectionName,
        Map.of(
            "vectors", Map.of(
                "size", embeddingProviderChain.dimensions(),
                "distance", "Cosine"
            )
        ),
        "PUT"
    );
  }

  private Map<String, Object> buildFilter(Map<String, Object> payloadFilter) {
    List<Map<String, Object>> mustClauses = payloadFilter.entrySet().stream()
        .map(entry -> Map.<String, Object>of(
            "key", entry.getKey(),
            "match", Map.of("value", entry.getValue())
        ))
        .toList();
    return Map.of("must", mustClauses);
  }

  private Map<String, Object> sendRequest(String path, Object body) {
    return sendRequest(path, body, "POST");
  }

  private Map<String, Object> sendRequest(String path, Object body, String method) {
    try {
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(qdrantProperties.getBaseUrl() + path))
          .timeout(Duration.ofSeconds(10));
      if (!"DELETE".equals(method)) {
        builder.header("Content-Type", "application/json");
      }
      HttpRequest request = builder
          .method(method, requestBody(method, body))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400 && response.statusCode() != 409 && response.statusCode() != 404) {
        throw new IllegalStateException("Qdrant request failed: " + response.body());
      }
      if (response.body() == null || response.body().isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(response.body(), new TypeReference<>() {
      });
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Failed to talk to Qdrant.", exception);
    }
  }

  private HttpRequest.BodyPublisher requestBody(String method, Object body) throws IOException {
    if ("DELETE".equals(method)) {
      return HttpRequest.BodyPublishers.noBody();
    }
    return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
  }
}
