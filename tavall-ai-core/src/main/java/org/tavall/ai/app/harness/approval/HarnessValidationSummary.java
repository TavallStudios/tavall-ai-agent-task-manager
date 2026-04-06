package org.tavall.ai.app.harness.approval;

import org.tavall.ai.app.harness.cleanjava.CleanJavaValidationStageResult;

public record HarnessValidationSummary(
    String reportId,
    String status,
    String summary,
    CleanJavaValidationStageResult lint,
    CleanJavaValidationStageResult sourceShape,
    CleanJavaValidationStageResult architecture,
    CleanJavaValidationStageResult cycles
) {
}

