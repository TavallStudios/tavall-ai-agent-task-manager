package org.tavall.ai.app.model.computeruse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ComputerUseSessionSummary(
    String sessionId,
    String runnerId,
    String taskId,
    String workerTaskId,
    String scenarioId,
    String serverTarget,
    String chartId,
    String status,
    String latestSummary,
    String runnerSessionKey,
    List<String> expectedArtifacts,
    List<String> passFailGates,
    Map<String, Object> artifactPolicy,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt
) {
}

