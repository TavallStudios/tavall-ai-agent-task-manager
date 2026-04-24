package org.tavall.ai.app.harness.state;

public record HarnessStateSnapshot(
    HarnessTaskSchema taskSchema,
    HarnessAgentSchema agentSchema,
    HarnessPersistenceModel persistenceModel,
    HarnessDashboardModel dashboardModel
) {
}

