package com.agenttaskmanager.app.mcp.tools.orchestration;

import com.agenttaskmanager.app.model.orchestration.CleanupReviewTask;
import com.agenttaskmanager.app.model.orchestration.OverseerTaskBatch;
import com.agenttaskmanager.app.model.orchestration.TaskAssignment;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import java.util.List;

record CreateTaskBatchRequest(
    String projectKey,
    String sourceRepo,
    String title,
    boolean multiAgentEnabled,
    List<String> workerRoles
) {
}

record CreateTaskBatchResponse(OverseerTaskBatch batch) {
}

record ClaimWorkerTaskRequest(String taskId) {
}

record ClaimWorkerTaskResponse(WorkerTask workerTask) {
}

record AssignWorkerTaskRequest(String workerTaskId, String agentId, String transportKind, String sessionId) {
}

record AssignWorkerTaskResponse(TaskAssignment assignment) {
}

record WorkerTaskUpdateRequest(String workerTaskId, String summary) {
}

record WorkerTaskResponse(WorkerTask workerTask) {
}

record CleanupReviewTaskRequest(String taskId, String workerTaskId, String diffArtifactId) {
}

record CleanupReviewTaskResponse(CleanupReviewTask cleanupReviewTask) {
}
