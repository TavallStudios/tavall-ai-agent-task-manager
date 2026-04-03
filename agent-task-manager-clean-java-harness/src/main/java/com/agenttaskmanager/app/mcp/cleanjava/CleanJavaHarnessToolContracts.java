package com.agenttaskmanager.app.mcp.cleanjava;

import com.agenttaskmanager.app.harness.approval.HarnessApprovalGateResult;
import com.agenttaskmanager.app.harness.cleanjava.CleanJavaHarnessRunResult;
import com.agenttaskmanager.app.harness.cleanjava.CleanJavaTaskContext;
import com.agenttaskmanager.app.harness.routing.HarnessRoutingPlan;
import com.agenttaskmanager.app.harness.state.HarnessStateSnapshot;
import com.agenttaskmanager.app.harness.tools.HarnessToolBundleResult;
import java.util.List;
import java.util.Map;

record CleanJavaHarnessRequest(
    String taskId,
    String workerTaskId,
    String projectKey,
    String repoPath,
    String queryText
) {
}

record CleanJavaHarnessRepoPathRequest(String repoPath, Integer timeoutSeconds) {
}

record HarnessTaskRequest(
    String taskId,
    String type,
    String title,
    String description,
    String repoRef,
    String priority,
    String requestedBy,
    boolean requiresCleanupReview,
    boolean requiresIntegrationTests,
    boolean multiAgentEnabled,
    List<String> requestedWorkerTypes,
    List<String> changedFiles,
    String gitBase,
    String gitHead,
    Map<String, Object> codebaseInput,
    Map<String, Object> storedContextInput,
    Map<String, Object> ruleInput,
    Map<String, Object> liveDebugInput,
    Map<String, Object> metadata
) {
}

record HarnessTaskIdRequest(String taskId) {
}

record HarnessApprovalRequest(
    String taskId,
    String workerTaskId,
    String repoPath,
    String diffArtifactId,
    Integer workerExitCode,
    Boolean requiresIntegrationTests,
    Integer integrationTimeoutSeconds
) {
}

record HarnessRoutingResponse(HarnessRoutingPlan routingPlan) {
}

record HarnessStateResponse(HarnessStateSnapshot harnessState) {
}

record HarnessToolBundleResponse(HarnessToolBundleResult bundleResult) {
}

record HarnessApprovalResponse(HarnessApprovalGateResult gateResult) {
}

record CleanJavaTaskContextResponse(CleanJavaTaskContext taskContext) {
}

record CleanJavaHarnessRunResponse(CleanJavaHarnessRunResult runResult) {
}
