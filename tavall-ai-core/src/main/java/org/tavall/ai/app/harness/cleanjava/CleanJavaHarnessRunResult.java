package org.tavall.ai.app.harness.cleanjava;

import org.tavall.ai.app.model.validation.ValidationReport;

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

