package com.agenttaskmanager.app.harness.state;

public record HarnessStateSnapshot(
    HarnessTaskSchema taskSchema,
    HarnessAgentSchema agentSchema,
    HarnessPersistenceModel persistenceModel,
    HarnessDashboardModel dashboardModel
) {
}
