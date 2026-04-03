package com.agenttaskmanager.app.mcp.tools.context;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.model.orchestration.SharedTaskContext;
import java.util.List;
import java.util.Map;

record ContextTaskIdRequest(String taskId) {
}

record ContextThreadKeyRequest(String threadKey) {
}

record SemanticContextRequest(String projectKey, String queryText, Integer limit) {
}

record SiblingSummaryRequest(String taskId, String workerTaskId) {
}

record StoreSharedTaskContextRequest(
    String taskId,
    String workerTaskId,
    String contextKey,
    String visibility,
    String summary,
    Map<String, Object> payload
) {
}

record TaskContextResponse(Map<String, Object> payload) {
}

record ValidationHistoryResponse(List<?> items) {
}

record ChatStateResponse(Object detail) {
}

record SemanticContextResponse(List<RetrievedSemanticContext> items) {
}

record SiblingSummaryResponse(List<Map<String, Object>> items) {
}

record SharedTaskContextResponse(SharedTaskContext context) {
}

record SharedTaskContextsResponse(List<SharedTaskContext> items) {
}

record TextPayloadResponse(String body) {
}
