package org.tavall.ai.app.harness.state;

import org.tavall.ai.app.dashboard.model.DashboardSummary;
import java.util.Map;

public record HarnessDashboardModel(
    DashboardSummary dashboardSummary,
    Map<String, Long> workerTypeCounts,
    Map<String, Long> workerStatusCounts
) {
}

