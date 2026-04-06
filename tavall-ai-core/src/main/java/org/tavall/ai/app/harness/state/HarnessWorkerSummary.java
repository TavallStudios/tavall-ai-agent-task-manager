package org.tavall.ai.app.harness.state;

import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.model.orchestration.WorkerType;
import java.util.Map;

public record HarnessWorkerSummary(
    String workerTaskId,
    WorkerType workerType,
    String taskRole,
    TaskLifecycleStatus status,
    String assignedAgentId,
    boolean dead,
    Map<Object, Object> hotState
) {
}

