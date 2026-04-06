package org.tavall.ai.app.model.orchestration;

import java.time.OffsetDateTime;
import java.util.Map;

public record WorkerCheckIn(
    long checkInId,
    String workerTaskId,
    String taskId,
    String agentId,
    TaskLifecycleStatus status,
    String summary,
    Map<String, Object> details,
    OffsetDateTime createdAt
) {
}

