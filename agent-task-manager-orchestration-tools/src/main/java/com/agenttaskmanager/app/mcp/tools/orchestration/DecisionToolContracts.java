package com.agenttaskmanager.app.mcp.tools.orchestration;

import com.agenttaskmanager.app.model.orchestration.AutonomousCycleReport;
import com.agenttaskmanager.app.model.orchestration.OverseerDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.PatchDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.TaskMergeResult;

record PatchDecisionRequest(
    String taskId,
    String workerTaskId,
    String validationReportId,
    String cleanupReviewId,
    String diffArtifactId
) {
}

record OverseerDecisionRequest(String taskId, String workerTaskId, String decisionType, String status, String summary) {
}

record RunSummaryRequest(String taskId, String summary) {
}

record AutonomousRepoPathRequest(String repoPath) {
}

record MergeResultResponse(TaskMergeResult result) {
}

record PatchDecisionResponse(PatchDecisionRecord patchDecision) {
}

record OverseerDecisionResponse(OverseerDecisionRecord decision) {
}

record AutonomousCycleResponse(AutonomousCycleReport report) {
}
