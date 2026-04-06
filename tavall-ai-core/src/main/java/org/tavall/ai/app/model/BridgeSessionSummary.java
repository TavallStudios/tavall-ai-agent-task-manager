package org.tavall.ai.app.model;

import java.time.OffsetDateTime;

public record BridgeSessionSummary(
    String sessionId,
    String agentId,
    String clientName,
    String hostName,
    String repoPath,
    String bridgeTarget,
    String transport,
    String status,
    boolean online,
    OffsetDateTime lastSeenAt
) {
}

