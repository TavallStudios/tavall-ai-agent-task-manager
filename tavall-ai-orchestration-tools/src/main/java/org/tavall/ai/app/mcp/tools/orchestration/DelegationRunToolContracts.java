package org.tavall.ai.app.mcp.tools.orchestration;

import org.tavall.ai.app.model.orchestration.CodexDelegationRun;
import org.tavall.ai.app.model.orchestration.CodexDelegationRunSnapshot;
import java.util.List;
import java.util.Map;

record StartDelegationRunRequest(
    String taskId,
    String projectKey,
    String repoPath,
    String title,
    Map<String, Object> metadata
) {
}

record DelegationRunEventRequest(
    String runId,
    String eventType,
    String status,
    String summary,
    Map<String, Object> details
) {
}

record LoadDelegationRunRequest(String runId) {
}

record ListDelegationRunsRequest(Integer limit, String status) {
}

record CompleteDelegationRunRequest(
    String runId,
    String status,
    String summary,
    Map<String, Object> details
) {
}

record DelegationRunResponse(CodexDelegationRunSnapshot run) {
}

record DelegationRunListResponse(List<CodexDelegationRun> runs) {
}

