package org.tavall.ai.app.dashboard.model;

import java.time.OffsetDateTime;

public record WorkerDashboardCard(
    String workerTaskId,
    String taskId,
    String taskRole,
    String status,
    String agentId,
    String transportKind,
    OffsetDateTime lastCheckInAt,
    OffsetDateTime leaseExpiresAt,
    boolean dead
) {
}

