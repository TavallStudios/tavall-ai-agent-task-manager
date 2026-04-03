package com.agenttaskmanager.app.model.computeruse;

import java.time.OffsetDateTime;
import java.util.Map;

public record ComputerUseSessionArtifact(
    String artifactId,
    String sessionId,
    String artifactKind,
    String storageKey,
    String summary,
    Map<String, Object> metadata,
    OffsetDateTime createdAt
) {
}
