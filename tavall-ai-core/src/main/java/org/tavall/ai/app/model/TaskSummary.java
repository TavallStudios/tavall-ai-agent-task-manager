package org.tavall.ai.app.model;

import java.time.OffsetDateTime;

public record TaskSummary(
    String taskId,
    String projectKey,
    String sourceRepo,
    String taskKind,
    String title,
    String status,
    int priority,
    String ownerAgentId,
    boolean multiAgentEnabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    String latestCheckpointAgentId,
    String latestCheckpointStatus,
    String latestCheckpointSummary,
    OffsetDateTime latestCheckpointAt,
    String activeLeaseAgentId,
    String activeLeaseSessionId,
    OffsetDateTime activeLeaseExpiresAt
) {
}


