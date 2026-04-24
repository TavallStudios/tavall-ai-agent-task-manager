package org.tavall.ai.app.model.bridge;

import java.time.OffsetDateTime;
import java.util.Map;

public record BridgeAutomationCommandSummary(
    String commandRequestId,
    String sessionId,
    String targetAgentId,
    String repoPath,
    String bridgeTarget,
    String commandId,
    String isolationClass,
    String status,
    String requestedBy,
    String requestedFrom,
    String latestSummary,
    Map<String, Object> arguments,
    Map<String, Object> result,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime completedAt
) {
}

