package com.agenttaskmanager.app.dashboard.model;

import java.time.OffsetDateTime;

public record ChatDashboardCard(
    String threadKey,
    String repoPath,
    String bridgeTarget,
    String latestStatus,
    OffsetDateTime lastMessageAt,
    boolean dead
) {
}
