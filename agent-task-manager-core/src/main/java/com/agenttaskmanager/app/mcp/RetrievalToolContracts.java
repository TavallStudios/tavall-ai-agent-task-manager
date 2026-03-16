package com.agenttaskmanager.app.mcp;

import java.util.Map;

record StoreEmbeddingRequest(
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

record AttachSemanticContextRequest(
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

record EmbeddingResponse(String embeddingId) {
}

record ValidationSummaryCacheResponse(Map<String, Object> payload) {
}
