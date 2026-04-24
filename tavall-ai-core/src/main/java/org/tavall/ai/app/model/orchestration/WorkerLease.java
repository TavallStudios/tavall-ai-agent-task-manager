package org.tavall.ai.app.model.orchestration;

import java.time.OffsetDateTime;

public record WorkerLease(
    String workerTaskId,
    String taskId,
    String agentId,
    String sessionId,
    String leaseToken,
    WorkerTransportKind transportKind,
    OffsetDateTime acquiredAt,
    OffsetDateTime heartbeatAt,
    OffsetDateTime expiresAt
) {
}

