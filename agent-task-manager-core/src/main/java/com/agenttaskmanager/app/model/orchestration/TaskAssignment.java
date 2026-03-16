package com.agenttaskmanager.app.model.orchestration;

public record TaskAssignment(
    String taskId,
    String workerTaskId,
    String agentId,
    WorkerTransportKind transportKind,
    String leaseToken
) {
}
