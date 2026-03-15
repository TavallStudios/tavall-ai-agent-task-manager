package com.agenttaskmanager.app.persistence.mongo;

import com.agenttaskmanager.app.config.MongoProperties;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.springframework.stereotype.Repository;

@Repository
public class ArtifactDocumentStore {

  private final MongoDatabase database;

  public ArtifactDocumentStore(MongoClient mongoClient, MongoProperties mongoProperties) {
    this.database = mongoClient.getDatabase(mongoProperties.getDatabase());
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
        .append("taskId", taskId)
        .append("workerTaskId", workerTaskId)
        .append("artifactKind", artifactKind)
        .append("body", body)
        .append("metadata", metadata)
        .append("updatedAt", OffsetDateTime.now().toString());
    artifacts().replaceOne(Filters.eq("_id", artifactId), document, new ReplaceOptions().upsert(true));
  }

  public Optional<Document> loadArtifactBody(String artifactId) {
    return Optional.ofNullable(artifacts().find(Filters.eq("_id", artifactId)).first());
  }

  public void storeChatSnapshot(String snapshotId, String threadKey, String status, Map<String, Object> payload) {
    Document document = new Document("_id", snapshotId)
        .append("threadKey", threadKey)
        .append("status", status)
        .append("payload", payload)
        .append("updatedAt", OffsetDateTime.now().toString());
    chatSnapshots().replaceOne(Filters.eq("_id", snapshotId), document, new ReplaceOptions().upsert(true));
  }

  private MongoCollection<Document> artifacts() {
    return database.getCollection("task_artifact_documents");
  }

  private MongoCollection<Document> chatSnapshots() {
    return database.getCollection("chat_snapshots");
  }
}
