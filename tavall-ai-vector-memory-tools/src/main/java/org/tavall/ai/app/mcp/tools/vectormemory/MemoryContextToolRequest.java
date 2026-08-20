package org.tavall.ai.app.mcp.tools.vectormemory;

import java.util.Map;

public record MemoryContextToolRequest(
    String projectId,
    String threadKey,
    String sessionId,
    String requestedBy,
    String requestedFrom,
    String repoPath,
    String queryText,
    Map<String, Object> metadata
) {
}
