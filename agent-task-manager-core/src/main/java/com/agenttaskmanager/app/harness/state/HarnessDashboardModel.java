package com.agenttaskmanager.app.harness.state;

import com.agenttaskmanager.app.dashboard.model.DashboardSummary;
import java.util.Map;

public record HarnessDashboardModel(
    DashboardSummary dashboardSummary,
    Map<String, Long> workerTypeCounts,
    Map<String, Long> workerStatusCounts
) {
}
