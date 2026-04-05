package com.agenttaskmanager.app.mcp.tools.validation;

record ValidationRequest(String taskId, String workerTaskId) {
}

record SpoonValidationRequest(String taskId, String workerTaskId, String repoPath) {
}

record JavaLintValidationRequest(String taskId, String workerTaskId, String repoPath) {
}

record IntegrationRepoPathRequest(String repoPath, Integer timeoutSeconds) {
}

record PatchScopeRequest(String diffBody) {
}

record CleanupReviewIdRequest(String cleanupReviewId) {
}

record PatchScopeResponse(boolean allowed) {
}
