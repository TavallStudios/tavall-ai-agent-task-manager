package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.model.orchestration.ArtifactRecord;
import com.agenttaskmanager.app.persistence.mongo.ArtifactDocumentStore;
import com.agenttaskmanager.app.persistence.postgres.TaskArtifactRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.springframework.stereotype.Service;

@Service
public class ArtifactService {

  private final TaskArtifactRepository taskArtifactRepository;
  private final ArtifactDocumentStore artifactDocumentStore;

  public ArtifactService(
      TaskArtifactRepository taskArtifactRepository,
      ArtifactDocumentStore artifactDocumentStore
  ) {
    this.taskArtifactRepository = taskArtifactRepository;
    this.artifactDocumentStore = artifactDocumentStore;
  }

  public ArtifactRecord writeArtifact(
      String taskId,
      String workerTaskId,
      String artifactKind,
      String summary,
      String body,
      Map<String, Object> metadata
  ) {
    String storageKey = "mongo:" + UUID.randomUUID();
    ArtifactRecord artifact = taskArtifactRepository.storeArtifact(
        taskId,
        workerTaskId,
        artifactKind,
        "mongodb",
        storageKey,
        summary,
        metadata
    );
    artifactDocumentStore.storeArtifactBody(
        artifact.artifactId(),
        taskId,
        workerTaskId,
        artifactKind,
        body,
        metadata
    );
    return artifact;
  }

  public ArtifactRecord storeDiffArtifact(
      String taskId,
      String workerTaskId,
      String diffBody,
      Map<String, Object> metadata
  ) {
    return writeArtifact(taskId, workerTaskId, "diff", "Captured worker diff", diffBody, metadata);
  }

  public Optional<String> readArtifact(String artifactId) {
    return artifactDocumentStore.loadArtifactBody(artifactId)
        .map(document -> document.getString("body"));
  }

  public Optional<Document> loadArtifactDocument(String artifactId) {
    return artifactDocumentStore.loadArtifactBody(artifactId);
  }

  public List<ArtifactRecord> loadTaskArtifacts(String taskId, String workerTaskId) {
    return taskArtifactRepository.listArtifacts(taskId, workerTaskId);
  }
}
