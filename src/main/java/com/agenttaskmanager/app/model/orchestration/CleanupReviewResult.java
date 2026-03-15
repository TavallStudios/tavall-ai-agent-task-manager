package com.agenttaskmanager.app.model.orchestration;

import java.time.OffsetDateTime;
import java.util.List;

public record CleanupReviewResult(
    String cleanupReviewId,
    String taskId,
    String workerTaskId,
    TaskLifecycleStatus status,
    String summary,
    List<String> findings,
    OffsetDateTime completedAt
) {
}
