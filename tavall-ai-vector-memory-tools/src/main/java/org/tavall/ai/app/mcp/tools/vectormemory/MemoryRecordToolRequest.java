package org.tavall.ai.app.mcp.tools.vectormemory;

import java.util.List;
import java.util.Map;

record MemoryRecordToolRequest(
    String projectId,
    String threadKey,
    String sessionId,
    String requestedBy,
    String requestedFrom,
    String repoPath,
    String scope,
    String kind,
    String title,
    String summary,
    List<String> facts,
    Integer importance,
    String sensitivity,
    String consentLevel,
    String sourceReference,
    String supersedesMemoryId,
    Map<String, Object> metadata
) {
}
