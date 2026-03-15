package com.agenttaskmanager.app.dashboard.model;

import java.time.OffsetDateTime;

public record PatchDecisionDashboardCard(
    String taskId,
    String workerTaskId,
    String status,
    String summary,
    String decisionBy,
    OffsetDateTime updatedAt
) {
}
