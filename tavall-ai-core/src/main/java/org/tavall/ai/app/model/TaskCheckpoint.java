package org.tavall.ai.app.model;

import java.time.OffsetDateTime;

public record TaskCheckpoint(
    long checkpointId,
    String agentId,
    String checkpointKind,
    String status,
    String summary,
    OffsetDateTime createdAt
) {
}


