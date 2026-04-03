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
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class QdrantContextStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(QdrantContextStore.class);

  private final HttpClient httpClient;
  private final EmbeddingProviderChain embeddingProviderChain;
  private final InMemoryQdrantStore inMemoryQdrantStore;
  private final ObjectMapper objectMapper;
  private final QdrantProperties qdrantProperties;
  private final AtomicBoolean localFallbackEnabled = new AtomicBoolean();

  public QdrantContextStore(
      HttpClient httpClient,
      EmbeddingProviderChain embeddingProviderChain,
      InMemoryQdrantStore inMemoryQdrantStore,
      ObjectMapper objectMapper,
      QdrantProperties qdrantProperties
  ) {
    this.httpClient = httpClient;
    this.embeddingProviderChain = embeddingProviderChain;
    this.inMemoryQdrantStore = inMemoryQdrantStore;
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

  public String upsertContext(String collectionName, String pointId, String kind, String body, Map<String, Object> payload) {
    EmbeddingVectorResult embedding = embeddingProviderChain.embed(kind, body, EmbeddingPurpose.RETRIEVAL_DOCUMENT);
    Map<String, Object> fullPayload = new LinkedHashMap<>();
    if (payload != null) {
      fullPayload.putAll(payload);
    }
    fullPayload.put("kind", kind);
    fullPayload.put("body", body);
    fullPayload.put("chunkText", body);
    fullPayload.put("embeddingProvider", embedding.providerId());
    fullPayload.put("embeddingModel", embedding.modelName());
    fullPayload.put("embeddingDimensions", embedding.vector().size());
    fullPayload.put("embeddingPurpose", EmbeddingPurpose.RETRIEVAL_DOCUMENT.name());

    Map<String, Object> point = Map.of(
        "id", pointId,
        "vector", embedding.vector(),
        "payload", fullPayload
    );
    if (shouldUseLocalFallback()) {
      inMemoryQdrantStore.upsert(collectionName, pointId, embedding.vector(), fullPayload);
      return pointId;
    }
    try {
      ensureCollection(collectionName);
      sendRequest(
          "/collections/" + collectionName + "/points?wait=true",
          Map.of("points", List.of(point)),
          "PUT"
      );
    } catch (IllegalStateException exception) {
      activateLocalFallback(exception);
      inMemoryQdrantStore.upsert(collectionName, pointId, embedding.vector(), fullPayload);
    }
    return pointId;
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(String collectionName, String queryText, int limit) {
    return searchRelatedContexts(collectionName, queryText, limit, Map.of(), EmbeddingPurpose.RETRIEVAL_QUERY);
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(String collectionName, String queryText, int limit, Map<String, Object> payloadFilter) {
    return searchRelatedContexts(collectionName, queryText, limit, payloadFilter, EmbeddingPurpose.RETRIEVAL_QUERY);
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(
      String collectionName,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter,
      EmbeddingPurpose queryPurpose
  ) {
    EmbeddingVectorResult embedding = embeddingProviderChain.embed(null, queryText, queryPurpose);
    if (shouldUseLocalFallback()) {
      return inMemoryQdrantStore.search(collectionName, embedding.vector(), limit, payloadFilter);
    }
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("query", embedding.vector());
    requestBody.put("limit", limit);
    requestBody.put("with_payload", true);
    if (payloadFilter != null && !payloadFilter.isEmpty()) {
      requestBody.put("filter", buildFilter(payloadFilter));
    }
    try {
      ensureCollection(collectionName);
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
    } catch (IllegalStateException exception) {
      activateLocalFallback(exception);
      return inMemoryQdrantStore.search(collectionName, embedding.vector(), limit, payloadFilter);
    }
  }

  public void deleteContexts(String collectionName, Map<String, Object> payloadFilter) {
    if (payloadFilter == null || payloadFilter.isEmpty()) {
      return;
    }
    if (shouldUseLocalFallback()) {
      inMemoryQdrantStore.deleteByFilter(collectionName, payloadFilter);
      return;
    }
    try {
      ensureCollection(collectionName);
      sendRequest(
          "/collections/" + collectionName + "/points/delete?wait=true",
          Map.of("filter", buildFilter(payloadFilter))
      );
    } catch (IllegalStateException exception) {
      activateLocalFallback(exception);
      inMemoryQdrantStore.deleteByFilter(collectionName, payloadFilter);
    }
  }

  public void deleteCollection(String collectionName) {
    if (shouldUseLocalFallback()) {
      inMemoryQdrantStore.deleteCollection(collectionName);
      return;
    }
    try {
      sendRequest("/collections/" + collectionName, Map.of(), "DELETE");
    } catch (IllegalStateException exception) {
      activateLocalFallback(exception);
      inMemoryQdrantStore.deleteCollection(collectionName);
    }
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
          .uri(URI.create(collectionBaseUrl(path)))
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

  private String collectionBaseUrl(String path) {
    return qdrantProperties.getBaseUrl() + path;
  }

  private boolean shouldUseLocalFallback() {
    return localFallbackEnabled.get() || !StringUtils.hasText(qdrantProperties.getBaseUrl());
  }

  private void activateLocalFallback(IllegalStateException exception) {
    if (localFallbackEnabled.compareAndSet(false, true)) {
      LOGGER.warn("Qdrant unavailable. Falling back to in-memory semantic storage: {}", exception.getMessage());
    }
  }
}
