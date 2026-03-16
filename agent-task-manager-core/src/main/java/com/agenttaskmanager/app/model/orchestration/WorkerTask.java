package com.agenttaskmanager.app.model.orchestration;

import java.time.OffsetDateTime;
import java.util.Map;

public record WorkerTask(
    String workerTaskId,
    String taskId,
    String parentWorkerTaskId,
    WorkerType workerType,
    String taskRole,
    String title,
    TaskLifecycleStatus status,
    String assignedAgentId,
    WorkerTransportKind transportKind,
    int attemptCount,
    int maxAttempts,
    String latestSummary,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime lastCheckInAt,
    OffsetDateTime completedAt
) {
}
