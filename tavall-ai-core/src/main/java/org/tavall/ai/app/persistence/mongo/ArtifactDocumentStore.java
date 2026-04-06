package org.tavall.ai.app.persistence.mongo;

import org.tavall.ai.app.config.MongoProperties;
import org.tavall.ai.app.console.Log;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ArtifactDocumentStore {

  private final MongoDatabase database;
  private final AtomicBoolean localFallbackEnabled = new AtomicBoolean();
  private final ConcurrentMap<String, Document> artifactDocuments = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Document> chatSnapshotDocuments = new ConcurrentHashMap<>();

  public ArtifactDocumentStore(ObjectProvider<MongoClient> mongoClientProvider, MongoProperties mongoProperties) {
    MongoClient mongoClient = mongoClientProvider.getIfAvailable();
    this.database = mongoClient != null && StringUtils.hasText(mongoProperties.getUri())
        ? mongoClient.getDatabase(mongoProperties.getDatabase())
        : null;
  }

  public void storeArtifactBody(
      String artifactId,
      String taskId,
      String workerTaskId,
      String artifactKind,
      String body,
      Map<String, Object> metadata
  ) {
    Document document = new Document("_id", artifactId)
        .append("taskId", taskId == null ? "" : taskId)
        .append("workerTaskId", workerTaskId == null ? "" : workerTaskId)
        .append("artifactKind", artifactKind)
        .append("body", body)
        .append("metadata", metadata == null ? Map.of() : new LinkedHashMap<>(metadata))
        .append("updatedAt", OffsetDateTime.now().toString());
    if (shouldUseLocalFallback()) {
      artifactDocuments.put(artifactId, copy(document));
      return;
    }
    try {
      artifacts().replaceOne(Filters.eq("_id", artifactId), document, new ReplaceOptions().upsert(true));
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      artifactDocuments.put(artifactId, copy(document));
    }
  }

  public void storeLearningArtifactBody(
      String artifactId,
      String artifactKind,
      String body,
      Map<String, Object> metadata
  ) {
    storeArtifactBody(artifactId, "", "", artifactKind, body, metadata);
  }

  public Optional<Document> loadArtifactBody(String artifactId) {
    if (shouldUseLocalFallback()) {
      return Optional.ofNullable(copy(artifactDocuments.get(artifactId)));
    }
    try {
      return Optional.ofNullable(artifacts().find(Filters.eq("_id", artifactId)).first());
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      return Optional.ofNullable(copy(artifactDocuments.get(artifactId)));
    }
  }

  public void storeChatSnapshot(String snapshotId, String threadKey, String status, Map<String, Object> payload) {
    Document document = new Document("_id", snapshotId)
        .append("threadKey", threadKey)
        .append("status", status)
        .append("payload", payload == null ? Map.of() : new LinkedHashMap<>(payload))
        .append("updatedAt", OffsetDateTime.now().toString());
    if (shouldUseLocalFallback()) {
      chatSnapshotDocuments.put(snapshotId, copy(document));
      return;
    }
    try {
      chatSnapshots().replaceOne(Filters.eq("_id", snapshotId), document, new ReplaceOptions().upsert(true));
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      chatSnapshotDocuments.put(snapshotId, copy(document));
    }
  }

  private MongoCollection<Document> artifacts() {
    return database.getCollection("task_artifact_documents");
  }

  private MongoCollection<Document> chatSnapshots() {
    return database.getCollection("chat_snapshots");
  }

  private boolean shouldUseLocalFallback() {
    return localFallbackEnabled.get() || database == null;
  }

  private void activateLocalFallback(RuntimeException exception) {
    if (localFallbackEnabled.compareAndSet(false, true)) {
      Log.warn("Mongo artifact store unavailable. Falling back to in-memory storage: {}", exception.getMessage());
    }
  }

  private Document copy(Document document) {
    if (document == null) {
      return null;
    }
    return Document.parse(document.toJson());
  }
}

