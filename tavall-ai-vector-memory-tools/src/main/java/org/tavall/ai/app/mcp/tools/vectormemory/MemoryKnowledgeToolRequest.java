package org.tavall.ai.app.mcp.tools.vectormemory;

import java.util.Map;

public record MemoryKnowledgeToolRequest(
    String projectId,
    String repoPath,
    String queryText,
    Integer limit,
    Map<String, Object> metadata
) {
}
