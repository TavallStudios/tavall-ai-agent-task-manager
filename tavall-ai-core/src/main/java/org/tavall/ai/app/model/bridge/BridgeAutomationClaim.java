package org.tavall.ai.app.model.bridge;

import java.time.OffsetDateTime;
import java.util.Map;

public record BridgeAutomationClaim(
    String commandRequestId,
    String sessionId,
    String targetAgentId,
    String repoPath,
    String bridgeTarget,
    String commandId,
    String isolationClass,
    Map<String, Object> arguments,
    String requestedBy,
    String requestedFrom,
    OffsetDateTime createdAt
) {
}

