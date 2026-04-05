package com.agenttaskmanager.app.model.orchestration;

import java.time.OffsetDateTime;
import java.util.Map;

public record CodexDelegationRun(
    String runId,
    String taskId,
    String projectKey,
    String repoPath,
    String title,
    TaskLifecycleStatus status,
    String summary,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime completedAt
) {
}
