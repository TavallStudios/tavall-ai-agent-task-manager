package com.agenttaskmanager.app.model.orchestration;

import java.time.OffsetDateTime;

public record WorkerRunSummary(
    String workerTaskId,
    String status,
    String summary,
    String diffArtifactId,
    OffsetDateTime completedAt
) {
}
