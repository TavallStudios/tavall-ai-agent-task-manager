package org.tavall.ai.app.model.hytalelearning;

import java.util.Map;

public record HytaleVisualAnchorRequest(
    String machineId,
    String clientProfileId,
    String serverTarget,
    String scenarioId,
    String anchorKey,
    String sourceWindow,
    Map<String, Object> normalizedRegion,
    String description,
    double confidence,
    String captureBase64,
    Map<String, Object> metadata
) {
}

