package com.agenttaskmanager.app.model.bridge;

import java.time.OffsetDateTime;
import java.util.Map;

public record BridgeSessionRecord(
    String sessionId,
    String agentId,
    String clientName,
    String hostName,
    String repoPath,
    String bridgeTarget,
    String transport,
    String status,
    boolean online,
    OffsetDateTime lastSeenAt,
    Map<String, Object> capabilities
) {
}
