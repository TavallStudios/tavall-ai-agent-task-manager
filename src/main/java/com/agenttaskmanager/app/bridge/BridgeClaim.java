package com.agenttaskmanager.app.bridge;

public record BridgeClaim(
    String requestId,
    String projectKey,
    String repoPath,
    String requestedBy,
    String requestedFrom,
    String executionMode,
    String promptText
) {
}

