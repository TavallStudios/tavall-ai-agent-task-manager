package org.tavall.ai.app.model.computeruse;

import java.util.List;
import java.util.Map;

public record ComputerUseSessionRequest(
    String runnerId,
    String taskId,
    String workerTaskId,
    String scenarioId,
    String serverTarget,
    String chartId,
    List<String> expectedArtifacts,
    List<String> passFailGates,
    Map<String, Object> artifactPolicy,
    Map<String, Object> metadata
) {
}

