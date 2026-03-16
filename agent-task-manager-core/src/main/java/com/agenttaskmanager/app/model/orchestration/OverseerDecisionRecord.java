package com.agenttaskmanager.app.model.orchestration;

import java.time.OffsetDateTime;
import java.util.Map;

public record OverseerDecisionRecord(
    long decisionId,
    String taskId,
    String workerTaskId,
    String decisionType,
    TaskLifecycleStatus status,
    String summary,
    Map<String, Object> details,
    OffsetDateTime createdAt
) {
}
