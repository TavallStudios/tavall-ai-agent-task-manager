package com.agenttaskmanager.app.computeruse;

import com.agenttaskmanager.app.model.computeruse.ComputerUseSessionArtifact;
import com.agenttaskmanager.app.model.computeruse.ComputerUseSessionSummary;
import com.agenttaskmanager.app.persistence.mongo.ArtifactDocumentStore;
import com.agenttaskmanager.app.persistence.postgres.ComputerUseArtifactRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ComputerUseArtifactService {

  private final ComputerUseArtifactRepository artifactRepository;
  private final ArtifactDocumentStore artifactDocumentStore;

  public ComputerUseArtifactService(
      ComputerUseArtifactRepository artifactRepository,
      ArtifactDocumentStore artifactDocumentStore
  ) {
    this.artifactRepository = artifactRepository;
    this.artifactDocumentStore = artifactDocumentStore;
  }

  public ComputerUseSessionArtifact storeBase64Artifact(
      ComputerUseSessionSummary session,
      String artifactKind,
      String summary,
      String base64Body,
      Map<String, Object> metadata
  ) {
    ComputerUseSessionArtifact artifact = artifactRepository.createArtifact(
        session.sessionId(),
        artifactKind,
        artifactKind + ":" + session.sessionId(),
        summary,
        metadata
    );
    Map<String, Object> documentMetadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
    documentMetadata.put("sessionId", session.sessionId());
    artifactDocumentStore.storeArtifactBody(
        artifact.artifactId(),
        session.taskId(),
        session.workerTaskId(),
        artifactKind,
        base64Body == null ? "" : base64Body,
        documentMetadata
    );
    return artifact;
  }
}
