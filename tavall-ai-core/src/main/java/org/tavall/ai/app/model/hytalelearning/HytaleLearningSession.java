package org.tavall.ai.app.model.hytalelearning;

import java.time.OffsetDateTime;
import java.util.Map;

public record HytaleLearningSession(
    String sessionId,
    String bridgeSessionId,
    String machineId,
    String clientProfileId,
    String clientInstallPath,
    String serverTarget,
    String scenarioId,
    String status,
    String latestSummary,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime completedAt
) {
}

