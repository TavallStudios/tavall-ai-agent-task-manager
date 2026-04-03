package com.agenttaskmanager.app.model.hytalelearning;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record HytalePlaybook(
    String playbookId,
    String machineId,
    String clientProfileId,
    String serverTarget,
    String scenarioId,
    String name,
    String targetWindow,
    List<Map<String, Object>> actions,
    List<String> expectedAnchors,
    Map<String, Object> failureRecovery,
    boolean approved,
    boolean pinned,
    String latestSummary,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime approvedAt,
    String approvedBy,
    OffsetDateTime pinnedAt,
    String pinnedBy
) {
}
