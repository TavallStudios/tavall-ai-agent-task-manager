package org.tavall.ai.app.harness.approval;

import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
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

