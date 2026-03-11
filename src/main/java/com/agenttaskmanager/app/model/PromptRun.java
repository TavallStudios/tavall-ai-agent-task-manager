package com.agenttaskmanager.app.model;

import java.time.OffsetDateTime;

public record PromptRun(
    long runId,
    String agentSessionId,
    String bridgeName,
    String status,
    Integer exitCode,
    String summary,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime completedAt
) {
}

