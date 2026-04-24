package org.tavall.ai.app.model.orchestration;

import java.time.OffsetDateTime;
import java.util.Map;

public record CodexDelegationStep(
    String stepId,
    String runId,
    String eventType,
    TaskLifecycleStatus status,
    String summary,
    Map<String, Object> details,
    OffsetDateTime createdAt
) {
}

