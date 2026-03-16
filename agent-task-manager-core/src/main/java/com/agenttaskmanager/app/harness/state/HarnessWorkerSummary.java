package com.agenttaskmanager.app.harness.state;

import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerType;
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
