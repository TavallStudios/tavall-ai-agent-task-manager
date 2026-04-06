package org.tavall.ai.app.model;

import java.time.OffsetDateTime;

public record PromptRequestSummary(
    String requestId,
    String projectKey,
    String repoPath,
    String bridgeTarget,
    String threadKey,
    String requestedBy,
    String requestedFrom,
    String targetAgentId,
    String executionMode,
    String status,
    String promptPreview,
    String latestSummary,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime completedAt,
    Long latestRunId,
    String latestRunStatus,
    String latestRunSummary,
    OffsetDateTime latestMessageAt
) {
}

