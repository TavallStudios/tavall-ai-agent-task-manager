package org.tavall.ai.app.model.orchestration;

import org.tavall.ai.app.dashboard.model.DashboardSummary;
import java.time.OffsetDateTime;
import java.util.List;

public record AutonomousCycleReport(
    int workerRuns,
    List<DeadWorkerRecord> deadWorkers,
    List<String> processedBatchIds,
    List<String> completedBatchIds,
    List<String> failedBatchIds,
    List<String> patchDecisionIds,
    DashboardSummary dashboardSummary,
    OffsetDateTime completedAt
) {
}

