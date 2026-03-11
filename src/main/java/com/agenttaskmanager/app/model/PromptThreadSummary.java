package com.agenttaskmanager.app.model;

import java.time.OffsetDateTime;

public record PromptThreadSummary(
    String threadKey,
    String projectKey,
    String repoPath,
    String bridgeTarget,
    String threadSessionId,
    String lastRequestId,
    String latestRequestStatus,
    String latestRequestSummary,
    String latestPromptPreview,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime lastMessageAt
) {
}
