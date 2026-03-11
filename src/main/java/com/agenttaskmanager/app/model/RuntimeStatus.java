package com.agenttaskmanager.app.model;

public record RuntimeStatus(
    long taskCount,
    long queuedPromptCount,
    boolean multiAgentEnabled,
    boolean redisReachable,
    String redisNamespace,
    boolean bridgeEnabled,
    boolean bridgeOnline,
    String bridgeAgentId,
    String bridgeSessionId,
    String bridgeSessionStatus,
    String bridgeActiveRequestId,
    Long bridgeActiveRunId
) {
}
