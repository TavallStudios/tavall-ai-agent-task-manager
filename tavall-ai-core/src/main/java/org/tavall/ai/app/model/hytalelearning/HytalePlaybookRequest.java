package org.tavall.ai.app.model.hytalelearning;

import java.util.List;
import java.util.Map;

public record HytalePlaybookRequest(
    String machineId,
    String clientProfileId,
    String serverTarget,
    String scenarioId,
    String name,
    String targetWindow,
    List<Map<String, Object>> actions,
    List<String> expectedAnchors,
    Map<String, Object> failureRecovery,
    String latestSummary,
    Map<String, Object> metadata
) {
}

