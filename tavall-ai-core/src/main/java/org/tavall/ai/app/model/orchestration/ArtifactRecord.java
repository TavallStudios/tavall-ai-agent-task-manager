package org.tavall.ai.app.model.orchestration;

import java.time.OffsetDateTime;
import java.util.Map;

public record ArtifactRecord(
    String artifactId,
    String taskId,
    String workerTaskId,
    String artifactKind,
    String storageBackend,
    String storageKey,
    String summary,
    Map<String, Object> metadata,
    OffsetDateTime createdAt
) {
}

