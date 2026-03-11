package com.agenttaskmanager.app.model;

public record RuntimeStatus(
    long taskCount,
    long queuedPromptCount,
    boolean multiAgentEnabled,
    boolean redisReachable,
    String redisNamespace
) {
}

