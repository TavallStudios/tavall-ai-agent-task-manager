package com.agenttaskmanager.app.mcp.tools.vectormemory;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.model.orchestration.SharedTaskContext;
import java.util.List;
import java.util.Map;

record VectorMemoryStoreTaskEmbeddingRequest(
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

record VectorMemorySemanticQueryRequest(String projectKey, String queryText, Integer limit) {
}

record VectorMemoryAttachSemanticContextRequest(
    String projectKey,
    String taskId,
    String workerTaskId,
    String contextKey,
    String visibility,
    String summary,
    String body,
    String kind,
    String domain,
    String contentType,
    Map<String, Object> payload
) {
}

record VectorMemoryEmbeddingResponse(String embeddingId) {
}

record VectorMemorySemanticContextResponse(List<RetrievedSemanticContext> items) {
}

record VectorMemoryTaskSemanticAttachmentResponse(SharedTaskContext context, List<String> pointIds) {
}
