package com.agenttaskmanager.app.model.orchestration;

import java.time.OffsetDateTime;

public record OverseerTaskBatch(
    String taskId,
    String projectKey,
    String sourceRepo,
    String title,
    TaskLifecycleStatus status,
    String overseerAgentId,
    boolean multiAgentEnabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
