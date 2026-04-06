package org.tavall.ai.app.orchestration.access;

import org.tavall.ai.app.loader.ServiceLoaders;
import org.tavall.ai.app.model.orchestration.ArtifactRecord;
import java.util.Map;
import java.util.Optional;

public interface ArtifactDependencyAccess {

  default ArtifactRecord writeArtifact(
      String taskId,
      String workerTaskId,
      String artifactKind,
      String summary,
      String body,
      Map<String, Object> metadata
  ) {
    return ServiceLoaders.artifactService().writeArtifact(
        taskId,
        workerTaskId,
        artifactKind,
        summary,
        body,
        metadata
    );
  }

  default Optional<String> readArtifact(String artifactId) {
    return ServiceLoaders.artifactService().readArtifact(artifactId);
  }
}

