package org.tavall.ai.app.mcp.tools.orchestration;

import org.tavall.ai.app.model.orchestration.AutonomousCycleReport;
import org.tavall.ai.app.model.orchestration.OverseerDecisionRecord;
import org.tavall.ai.app.model.orchestration.PatchDecisionRecord;
import org.tavall.ai.app.model.orchestration.TaskMergeResult;
import java.util.Map;

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

record MergeResultResponse(TaskMergeResult result, Map<String, Object> compatibility) {
}

record PatchDecisionResponse(PatchDecisionRecord patchDecision) {
}

record OverseerDecisionResponse(OverseerDecisionRecord decision) {
}

record AutonomousCycleResponse(AutonomousCycleReport report, Map<String, Object> compatibility) {
}

