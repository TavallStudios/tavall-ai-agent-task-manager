package com.agenttaskmanager.app.model.orchestration;

import java.time.OffsetDateTime;
import java.util.Map;

public record SharedTaskContext(
    String contextId,
    String taskId,
    String workerTaskId,
    String contextKey,
    String visibility,
    String summary,
    Map<String, Object> payload,
    OffsetDateTime updatedAt
) {
}
