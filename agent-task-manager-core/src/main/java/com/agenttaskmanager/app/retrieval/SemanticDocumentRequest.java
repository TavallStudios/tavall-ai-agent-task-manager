package com.agenttaskmanager.app.retrieval;

import java.util.Map;

public record SemanticDocumentRequest(
    String documentId,
    String taskId,
    String workerTaskId,
    String kind,
    String title,
    String content,
    SemanticCollectionDomain domain,
    SemanticContentType contentType,
    Map<String, Object> payload
) {
}
