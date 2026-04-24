package org.tavall.ai.app.model.hytalelearning;

import java.time.OffsetDateTime;
import java.util.Map;

public record HytaleVisualAnchor(
    String anchorId,
    String machineId,
    String clientProfileId,
    String serverTarget,
    String scenarioId,
    String anchorKey,
    String sourceWindow,
    Map<String, Object> normalizedRegion,
    String description,
    double confidence,
    String storageBackend,
    String storageKey,
    OffsetDateTime lastValidatedAt,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

