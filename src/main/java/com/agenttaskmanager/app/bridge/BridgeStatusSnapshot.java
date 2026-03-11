package com.agenttaskmanager.app.bridge;

public record BridgeStatusSnapshot(
    boolean enabled,
    boolean online,
    String agentId,
    String sessionId,
    String sessionStatus,
    String activeRequestId,
    Long activeRunId
) {
}

