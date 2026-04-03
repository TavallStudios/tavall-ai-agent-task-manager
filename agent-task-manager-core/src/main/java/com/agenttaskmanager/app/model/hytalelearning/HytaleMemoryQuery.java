package com.agenttaskmanager.app.model.hytalelearning;

public record HytaleMemoryQuery(
    String machineId,
    String clientProfileId,
    String serverTarget,
    String scenarioId,
    String queryText
) {
}
