package org.tavall.ai.app.model.orchestration;

import java.time.OffsetDateTime;
import java.util.Map;

public record PatchDecisionRecord(
    String patchDecisionId,
    String taskId,
    String workerTaskId,
    String validationReportId,
    String cleanupReviewId,
    String diffArtifactId,
    TaskLifecycleStatus status,
    String summary,
    String decisionBy,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

