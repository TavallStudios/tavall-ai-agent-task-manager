package com.agenttaskmanager.app.dashboard.model;

import java.time.OffsetDateTime;

public record ValidationDashboardCard(
    String taskId,
    String workerTaskId,
    String status,
    double complianceScore,
    String summary,
    OffsetDateTime completedAt
) {
}
