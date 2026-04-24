package org.tavall.ai.app.harness.cleanjava;

import org.tavall.ai.app.model.validation.ValidationViolation;
import java.util.List;

public record CleanJavaValidationStageResult(
    String stageName,
    String status,
    String summary,
    List<ValidationViolation> violations
) {
}

