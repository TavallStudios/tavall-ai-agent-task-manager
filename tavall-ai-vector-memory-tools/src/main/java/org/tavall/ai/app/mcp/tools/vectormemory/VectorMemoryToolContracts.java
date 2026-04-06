package org.tavall.ai.app.mcp.tools.vectormemory;

import java.util.Map;

record StoreVectorMemoryDocumentRequest(
    String projectKey,
    String taskId,
    String workerTaskId,
    String kind,
    String title,
    String body,
    String domain,
    String contentType,
    Map<String, Object> payload
) {
}

record AttachVectorMemoryDocumentRequest(
    String projectKey,
    String taskId,
    String workerTaskId,
    String contextKey,
    String summary,
    String body,
    String domain,
    String contentType
) {
}

record VectorMemorySearchResponse(String embeddingId) {
}

record PromptThreadMemoryQueryRequest(
    String projectKey,
    String threadKey,
    String queryText,
    Integer limit
) {
}

