package org.tavall.ai.app.dashboard.model;

import java.util.List;
import java.util.Map;

public record DashboardSummary(
    long activeChats,
    long deadChats,
    long activeWorkers,
    long deadWorkers,
    long queuedTasks,
    long runningTasks,
    long failedTasks,
    long completedTasks,
    long cleanupReviewsPending,
    long patchRejections,
    List<ChatDashboardCard> chats,
    List<WorkerDashboardCard> workers,
    List<TaskBatchDashboardCard> batches,
    List<ValidationDashboardCard> validations,
    List<PatchDecisionDashboardCard> patchDecisions,
    Map<String, Object> cacheStats
) {
}

