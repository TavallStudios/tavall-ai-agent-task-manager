package com.agenttaskmanager.app.harness.cleanjava;

import com.agenttaskmanager.app.model.validation.ValidationViolation;
import java.util.List;

public record CleanJavaValidationStageResult(
    String stageName,
    String status,
    String summary,
    List<ValidationViolation> violations
) {
}
