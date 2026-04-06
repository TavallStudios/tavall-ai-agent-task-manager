package org.tavall.ai.app.model;

import java.time.OffsetDateTime;

public record PromptRun(
    long runId,
    String agentSessionId,
    String bridgeName,
    String threadSessionId,
    String status,
    Integer exitCode,
    String summary,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime completedAt
) {
}

