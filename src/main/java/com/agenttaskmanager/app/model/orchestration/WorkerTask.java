package com.agenttaskmanager.app.model.orchestration;

import java.time.OffsetDateTime;

public record WorkerTask(
    String workerTaskId,
    String taskId,
    String parentWorkerTaskId,
    String taskRole,
    String title,
    TaskLifecycleStatus status,
    String assignedAgentId,
    WorkerTransportKind transportKind,
    int attemptCount,
    int maxAttempts,
    String latestSummary,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime lastCheckInAt,
    OffsetDateTime completedAt
) {
}
