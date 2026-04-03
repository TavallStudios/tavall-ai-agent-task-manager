package com.agenttaskmanager.app.mcp.tools.orchestration;

import com.agenttaskmanager.app.model.orchestration.CleanupReviewResult;
import com.agenttaskmanager.app.model.orchestration.DeadWorkerRecord;
import com.agenttaskmanager.app.model.orchestration.WorkerCheckIn;
import java.util.List;
import java.util.Map;

record WorkerCheckInRequest(
    String workerTaskId,
    String taskId,
    String agentId,
    String status,
    String summary,
    Map<String, Object> details
) {
}

record WorkerHeartbeatRequest(String workerTaskId, String agentId) {
}

record WorkerDeadRequest(String workerTaskId, String summary) {
}

record WorkerRegistrationRequest(
    String sessionId,
    String agentId,
    String hostName,
    String clientName,
    String repoPath,
    String transportKind
) {
}

record CleanupRegistrationRequest(String sessionId, String agentId, String hostName, String clientName) {
}

record CleanupReviewUpdateRequest(String cleanupReviewId, String status, String summary, List<String> findings) {
}

record CleanupReviewRequiredRequest(String cleanupReviewId, String reason) {
}

record CleanupApprovalRequest(String cleanupReviewId, String summary) {
}

record WorkerCheckInResponse(WorkerCheckIn checkIn) {
}

record DeadWorkerResponse(DeadWorkerRecord deadWorkerRecord) {
}

record CleanupReviewResultResponse(CleanupReviewResult cleanupReviewResult) {
}

record StatusResponse(String status) {
}
