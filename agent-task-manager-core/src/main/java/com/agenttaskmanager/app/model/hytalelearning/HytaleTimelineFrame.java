package com.agenttaskmanager.app.model.hytalelearning;

import java.time.OffsetDateTime;
import java.util.Map;

public record HytaleTimelineFrame(
    String frameId,
    String sessionId,
    String sourceWindow,
    String artifactKind,
    String storageBackend,
    String storageKey,
    String summary,
    Map<String, Object> metadata,
    OffsetDateTime createdAt
) {
}
