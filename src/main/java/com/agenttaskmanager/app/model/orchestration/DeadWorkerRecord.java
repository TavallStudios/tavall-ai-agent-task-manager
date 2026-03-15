package com.agenttaskmanager.app.model.orchestration;

import java.time.OffsetDateTime;

public record DeadWorkerRecord(
    String workerTaskId,
    String agentId,
    OffsetDateTime detectedAt,
    String summary
) {
}
