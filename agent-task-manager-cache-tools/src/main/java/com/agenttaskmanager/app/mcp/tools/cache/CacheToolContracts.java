package com.agenttaskmanager.app.mcp.tools.cache;

import java.util.Map;

record CacheTaskIdRequest(String taskId) {
}

record CacheValidationRequest(String taskId, String workerTaskId, String repoPath) {
}

record CacheValidationLookupRequest(String taskId, String workerTaskId) {
}

record CacheTaskContextResponse(Map<String, Object> payload) {
}

record CacheValidationSummaryResponse(Object payload) {
}

record CacheStatusResponse(String status) {
}
