package org.tavall.ai.app.model.hytalelearning;

import java.util.Map;

public record HytaleLearningSessionRequest(
    String bridgeSessionId,
    String machineId,
    String clientProfileId,
    String clientInstallPath,
    String serverTarget,
    String scenarioId,
    Map<String, Object> metadata
) {
}

