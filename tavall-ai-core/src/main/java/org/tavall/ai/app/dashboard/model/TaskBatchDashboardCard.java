package org.tavall.ai.app.dashboard.model;

import java.time.OffsetDateTime;

public record TaskBatchDashboardCard(
    String taskId,
    String projectKey,
    String title,
    String status,
    long queuedTasks,
    long runningTasks,
    long failedTasks,
    long completedTasks,
    OffsetDateTime updatedAt
) {
}

