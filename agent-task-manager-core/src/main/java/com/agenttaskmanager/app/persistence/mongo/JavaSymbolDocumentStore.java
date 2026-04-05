package com.agenttaskmanager.app.persistence.mongo;

import com.agenttaskmanager.app.config.MongoProperties;
import com.agenttaskmanager.app.console.Log;
import com.agenttaskmanager.app.harness.cleanjava.symbol.JavaContractDeltaReport;
import com.agenttaskmanager.app.harness.cleanjava.symbol.JavaReflectionAugmentationResult;
import com.agenttaskmanager.app.harness.cleanjava.symbol.JavaSymbolNeighborhood;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JavaSymbolDocumentStore {

  private final MongoDatabase database;
  private final ObjectMapper objectMapper;
  private final AtomicBoolean localFallbackEnabled = new AtomicBoolean();
  private final ConcurrentMap<String, Document> snapshotDocuments = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Document> contractDeltaDocuments = new ConcurrentHashMap<>();

  public JavaSymbolDocumentStore(
      ObjectProvider<MongoClient> mongoClientProvider,
      MongoProperties mongoProperties,
      ObjectMapper objectMapper
  ) {
    MongoClient mongoClient = mongoClientProvider.getIfAvailable();
    this.database = mongoClient != null && StringUtils.hasText(mongoProperties.getUri())
        ? mongoClient.getDatabase(mongoProperties.getDatabase())
        : null;
    this.objectMapper = objectMapper;
  }

  public void storeSnapshot(
      String phase,
      String correlationId,
      String projectKey,
      String taskId,
      String workerTaskId,
      String repoPath,
      String revision,
      JavaSymbolNeighborhood neighborhood,
      JavaReflectionAugmentationResult reflection,
      java.util.List<String> warnings
  ) {
    if (neighborhood == null) {
      return;
    }
    for (var profile : neighborhood.orderedProfiles()) {
      Document document = new Document("_id", correlationId + ":" + phase + ":" + profile.qualifiedName())
          .append("kind", "class-snapshot")
          .append("phase", phase)
          .append("correlationId", correlationId)
          .append("projectKey", blank(projectKey))
          .append("taskId", blank(taskId))
          .append("workerTaskId", blank(workerTaskId))
          .append("repoPath", blank(repoPath))
          .append("revision", blank(revision))
          .append("sourcePath", profile.sourcePath())
          .append("className", profile.qualifiedName())
          .append("neighborhoodTargetClassNames", neighborhood.targetClassNames())
          .append("reflectionAugmented", reflection != null && reflection.augmented())
          .append("warnings", warnings == null ? java.util.List.of() : warnings)
          .append("profile", raw(profile))
          .append("reflectionProfile", reflectionProfile(reflection, profile.qualifiedName()))
          .append("updatedAt", OffsetDateTime.now().toString());
      store("java_symbol_snapshots", snapshotDocuments, document);
    }
    Document neighborhoodDocument = new Document("_id", correlationId + ":" + phase + ":neighborhood")
        .append("kind", "neighborhood")
        .append("phase", phase)
        .append("correlationId", correlationId)
        .append("projectKey", blank(projectKey))
        .append("taskId", blank(taskId))
        .append("workerTaskId", blank(workerTaskId))
        .append("repoPath", blank(repoPath))
        .append("revision", blank(revision))
        .append("payload", raw(neighborhood))
        .append("updatedAt", OffsetDateTime.now().toString());
    store("java_symbol_snapshots", snapshotDocuments, neighborhoodDocument);
  }

  public void storeContractDelta(
      String correlationId,
      String projectKey,
      String taskId,
      String workerTaskId,
      String repoPath,
      JavaContractDeltaReport report
  ) {
    if (report == null) {
      return;
    }
    Document document = new Document("_id", correlationId + ":contract-delta")
        .append("correlationId", correlationId)
        .append("projectKey", blank(projectKey))
        .append("taskId", blank(taskId))
        .append("workerTaskId", blank(workerTaskId))
        .append("repoPath", blank(repoPath))
        .append("payload", raw(report))
        .append("updatedAt", OffsetDateTime.now().toString());
    store("java_contract_delta_reports", contractDeltaDocuments, document);
  }

  public boolean localFallbackEnabled() {
    return localFallbackEnabled.get() || database == null;
  }

  private Object reflectionProfile(JavaReflectionAugmentationResult reflection, String className) {
    if (reflection == null || reflection.profiles().isEmpty()) {
      return Map.of();
    }
    return reflection.profiles().stream()
        .filter(profile -> className.equals(profile.qualifiedName()))
        .findFirst()
        .map(this::raw)
        .orElse(Map.of());
  }

  private Map<String, Object> raw(Object value) {
    return objectMapper.convertValue(value, new TypeReference<>() {
    });
  }

  private void store(String collectionName, ConcurrentMap<String, Document> fallback, Document document) {
    if (localFallbackEnabled()) {
      fallback.put(document.getString("_id"), copy(document));
      return;
    }
    try {
      MongoCollection<Document> collection = database.getCollection(collectionName);
      collection.replaceOne(Filters.eq("_id", document.getString("_id")), document, new ReplaceOptions().upsert(true));
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      fallback.put(document.getString("_id"), copy(document));
    }
  }

  private void activateLocalFallback(RuntimeException exception) {
    if (localFallbackEnabled.compareAndSet(false, true)) {
      Log.warn("Mongo Java symbol store unavailable. Falling back to in-memory storage: {}", exception.getMessage());
    }
  }

  private Document copy(Document document) {
    return Document.parse(document.toJson());
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }
}
