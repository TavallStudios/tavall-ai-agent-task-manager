package org.tavall.ai.app.persistence.qdrant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.tavall.ai.app.config.QdrantProperties;
import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;

@Repository
public class QdrantContextStore {

  private static final int MAX_QUERY_CHARACTERS = 8_000;
  private static final int MAX_SEARCH_RESULTS = 50;

  private final EmbeddingProviderChain embeddingProviderChain;
  private final InMemoryQdrantStore inMemoryQdrantStore;
  private final QdrantRestClient restClient;

  public QdrantContextStore(
      HttpClient httpClient,
      EmbeddingProviderChain embeddingProviderChain,
      InMemoryQdrantStore inMemoryQdrantStore,
      ObjectMapper objectMapper,
      QdrantProperties qdrantProperties
  ) {
    this.embeddingProviderChain = embeddingProviderChain;
    this.inMemoryQdrantStore = inMemoryQdrantStore;
    this.restClient = new QdrantRestClient(httpClient, objectMapper, qdrantProperties);
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

  public String upsertContext(
      String collectionName,
      String pointId,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    EmbeddingVectorResult embedding = embeddingProviderChain.embed(
        kind,
        body,
        EmbeddingPurpose.RETRIEVAL_DOCUMENT
    );
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
    ensureCollection(collectionName);
    restClient.request(
        "/collections/" + collectionName + "/points?wait=true",
        Map.of("points", List.of(point)),
        "PUT"
    );
    return pointId;
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(
      String collectionName,
      String queryText,
      int limit
  ) {
    return searchRelatedContexts(collectionName, queryText, limit, Map.of(), EmbeddingPurpose.RETRIEVAL_QUERY);
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(
      String collectionName,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    return searchRelatedContexts(collectionName, queryText, limit, payloadFilter, EmbeddingPurpose.RETRIEVAL_QUERY);
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(
      String collectionName,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter,
      EmbeddingPurpose queryPurpose
  ) {
    String normalizedQuery = queryText == null ? "" : queryText.strip();
    if (normalizedQuery.isBlank()) {
      return List.of();
    }
    if (normalizedQuery.length() > MAX_QUERY_CHARACTERS) {
      normalizedQuery = normalizedQuery.substring(0, MAX_QUERY_CHARACTERS);
    }
    int normalizedLimit = Math.max(1, Math.min(MAX_SEARCH_RESULTS, limit));
    EmbeddingVectorResult embedding = embeddingProviderChain.embed(null, normalizedQuery, queryPurpose);
    return searchRelatedContexts(collectionName, embedding, normalizedLimit, payloadFilter);
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(
      String collectionName,
      EmbeddingVectorResult embedding,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    if (embedding == null || embedding.vector().isEmpty()) {
      throw new IllegalArgumentException("Qdrant search requires a non-empty embedding.");
    }
    if (embedding.vector().size() != embeddingProviderChain.dimensions()) {
      throw new IllegalArgumentException("Qdrant search embedding dimensions do not match the configured profile.");
    }
    int normalizedLimit = Math.max(1, Math.min(MAX_SEARCH_RESULTS, limit));
    if (shouldUseLocalFallback()) {
      return inMemoryQdrantStore.search(collectionName, embedding.vector(), normalizedLimit, payloadFilter);
    }
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("query", embedding.vector());
    requestBody.put("limit", normalizedLimit);
    requestBody.put("with_payload", true);
    if (payloadFilter != null && !payloadFilter.isEmpty()) {
      requestBody.put("filter", buildFilter(payloadFilter));
    }
    ensureCollection(collectionName);
    Map<String, Object> response = restClient.request(
        "/collections/" + collectionName + "/points/query",
        requestBody,
        "POST"
    );
    Object result = response.get("result");
    Object points = result instanceof Map<?, ?> resultMap ? resultMap.get("points") : result;
    if (!(points instanceof List<?> items)) {
      throw new IllegalStateException("Qdrant query response did not include a point list.");
    }
    return items.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(this::readContext)
        .toList();
  }

  public void deleteContexts(String collectionName, Map<String, Object> payloadFilter) {
    if (payloadFilter == null || payloadFilter.isEmpty()) {
      return;
    }
    if (shouldUseLocalFallback()) {
      inMemoryQdrantStore.deleteByFilter(collectionName, payloadFilter);
      return;
    }
    ensureCollection(collectionName);
    restClient.request(
        "/collections/" + collectionName + "/points/delete?wait=true",
        Map.of("filter", buildFilter(payloadFilter)),
        "POST"
    );
  }

  public void deleteCollection(String collectionName) {
    if (shouldUseLocalFallback()) {
      inMemoryQdrantStore.deleteCollection(collectionName);
      return;
    }
    restClient.request("/collections/" + collectionName, Map.of(), "DELETE", Set.of(404));
  }

  private void ensureCollection(String collectionName) {
    restClient.request(
        "/collections/" + collectionName,
        Map.of("vectors", Map.of(
            "size", embeddingProviderChain.dimensions(),
            "distance", "Cosine"
        )),
        "PUT",
        Set.of(409)
    );
    verifyCollection(collectionName);
  }

  private void verifyCollection(String collectionName) {
    Map<String, Object> response = restClient.request(
        "/collections/" + collectionName,
        Map.of(),
        "GET"
    );
    Object result = response.get("result");
    if (!(result instanceof Map<?, ?> resultMap)) {
      throw new IllegalStateException("Qdrant collection response did not include result details.");
    }
    Object config = resultMap.get("config");
    if (!(config instanceof Map<?, ?> configMap)) {
      throw new IllegalStateException("Qdrant collection response did not include config details.");
    }
    Object params = configMap.get("params");
    if (!(params instanceof Map<?, ?> paramsMap)) {
      throw new IllegalStateException("Qdrant collection response did not include vector parameters.");
    }
    Object vectors = paramsMap.get("vectors");
    if (!(vectors instanceof Map<?, ?> vectorMap)) {
      throw new IllegalStateException("Qdrant collection does not use a single dense vector schema.");
    }
    int size = number(vectorMap.get("size"), "Qdrant collection vector size");
    String distance = String.valueOf(vectorMap.get("distance"));
    if (size != embeddingProviderChain.dimensions() || !"Cosine".equalsIgnoreCase(distance)) {
      throw new IllegalStateException(
          "Qdrant collection schema does not match the configured embedding profile: size="
              + size + ", distance=" + distance
      );
    }
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

  private RetrievedSemanticContext readContext(Map<?, ?> item) {
    Object id = item.get("id");
    Object score = item.get("score");
    Object payload = item.get("payload");
    if (id == null || !(score instanceof Number) || !(payload instanceof Map<?, ?>)) {
      throw new IllegalStateException("Qdrant returned a malformed semantic result point.");
    }
    return new RetrievedSemanticContext(
        String.valueOf(id),
        ((Number) score).doubleValue(),
        (Map<String, Object>) payload
    );
  }

  private int number(Object value, String label) {
    if (!(value instanceof Number number) || number.intValue() <= 0) {
      throw new IllegalStateException(label + " is missing or invalid.");
    }
    return number.intValue();
  }

  private boolean shouldUseLocalFallback() {
    return !restClient.isConfigured();
  }

  public boolean isConfigured() {
    return restClient.isConfigured();
  }

  public boolean isLocalFallbackEnabled() {
    return !isConfigured();
  }

  public String lastFailure() {
    return restClient.lastFailure();
  }

  public boolean hasRecentFailure() {
    return restClient.hasRecentFailure();
  }

  public boolean hasSuccessfulRequest() {
    return restClient.hasSuccessfulRequest();
  }
}
