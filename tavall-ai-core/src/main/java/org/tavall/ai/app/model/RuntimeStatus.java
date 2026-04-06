package org.tavall.ai.app.model;

public record RuntimeStatus(
    long taskCount,
    long queuedPromptCount,
    boolean multiAgentEnabled,
    boolean redisReachable,
    String redisNamespace,
    boolean runnerEnabled,
    boolean runnerOnline,
    String runnerAgentId,
    String runnerSessionId,
    String runnerStatus,
    String activeRequestId,
    Long activeRunId
) {
}

