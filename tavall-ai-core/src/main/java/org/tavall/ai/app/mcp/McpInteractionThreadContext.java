package org.tavall.ai.app.mcp;

import java.util.Map;

public record McpInteractionThreadContext(
    String interactionType,
    String interactionName,
    String sessionId,
    String threadKey,
    String projectKey,
    String repoPath,
    String requestedBy,
    String requestedFrom,
    String requestSummary,
    String lookupText,
    Map<String, Object> requestPayload,
    Map<String, Object> meta
) {
}

