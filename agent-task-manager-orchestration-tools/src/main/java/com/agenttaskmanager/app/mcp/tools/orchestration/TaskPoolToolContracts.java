package com.agenttaskmanager.app.mcp.tools.orchestration;

import com.agenttaskmanager.app.model.orchestration.CleanupReviewTask;
import com.agenttaskmanager.app.model.orchestration.OverseerTaskBatch;
import com.agenttaskmanager.app.model.orchestration.TaskAssignment;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import java.util.List;
import java.util.Map;

record CreateTaskBatchRequest(
    String projectKey,
    String sourceRepo,
    String title,
    boolean multiAgentEnabled,
    List<String> workerRoles
) {
}

record ClaimWorkerTaskRequest(String taskId) {
}

record CreateTaskBatchResponse(OverseerTaskBatch batch, Map<String, Object> compatibility) {
}

record ClaimWorkerTaskResponse(WorkerTask workerTask, Map<String, Object> compatibility) {
}

record AssignWorkerTaskRequest(String workerTaskId, String agentId, String transportKind, String sessionId) {
}

record AssignWorkerTaskResponse(TaskAssignment assignment, Map<String, Object> compatibility) {
}

record WorkerTaskUpdateRequest(String workerTaskId, String summary) {
}

record WorkerTaskResponse(WorkerTask workerTask, Map<String, Object> compatibility) {
}

record CleanupReviewTaskRequest(String taskId, String workerTaskId, String diffArtifactId) {
}

record CleanupReviewTaskResponse(CleanupReviewTask cleanupReviewTask) {
}
