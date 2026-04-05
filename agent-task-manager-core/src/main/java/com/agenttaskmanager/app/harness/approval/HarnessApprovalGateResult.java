package com.agenttaskmanager.app.harness.approval;

import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import java.util.Map;

public record HarnessApprovalGateResult(
    String taskId,
    String workerTaskId,
    boolean approved,
    TaskLifecycleStatus taskStatus,
    boolean patchScopeAllowed,
    HarnessCleanupSummary cleanup,
    HarnessValidationSummary validation,
    HarnessJavaSymbolSummary javaSymbol,
    Map<String, Object> integrationTests,
    int workerExitCode,
    String diffArtifactId,
    String summary
) {
}
