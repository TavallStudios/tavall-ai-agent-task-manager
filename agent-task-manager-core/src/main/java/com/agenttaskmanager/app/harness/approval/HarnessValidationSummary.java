package com.agenttaskmanager.app.harness.approval;

import com.agenttaskmanager.app.harness.cleanjava.CleanJavaValidationStageResult;

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
