package com.agenttaskmanager.app.harness.cleanjava;

import com.agenttaskmanager.app.model.validation.ValidationReport;

public record CleanJavaHarnessRunResult(
    CleanJavaTaskContext taskContext,
    CleanJavaValidationStageResult lint,
    CleanJavaValidationStageResult sourceShape,
    CleanJavaValidationStageResult architecture,
    CleanJavaValidationStageResult cycles,
    ValidationReport storedReport,
    boolean readyForApproval
) {
}
