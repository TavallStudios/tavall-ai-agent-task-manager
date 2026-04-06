package org.tavall.ai.app.model.validation;

import java.time.OffsetDateTime;
import java.util.List;

public record ValidationReport(
    String reportId,
    String taskId,
    String workerTaskId,
    String status,
    double complianceScore,
    String summary,
    List<ValidationViolation> violations,
    OffsetDateTime completedAt
) {
}

