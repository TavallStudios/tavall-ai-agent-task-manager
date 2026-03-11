package com.agenttaskmanager.app.model;

import java.time.OffsetDateTime;

public record PromptRequestFull(
    String requestId,
    String projectKey,
    String repoPath,
    String requestedBy,
    String requestedFrom,
    String targetAgentId,
    String executionMode,
    String status,
    String promptText,
    String latestSummary,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime completedAt
) {
}

