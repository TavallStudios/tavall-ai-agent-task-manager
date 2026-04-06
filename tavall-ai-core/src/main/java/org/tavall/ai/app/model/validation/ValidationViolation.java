package org.tavall.ai.app.model.validation;

public record ValidationViolation(
    String ruleId,
    ValidationSeverity severity,
    String targetType,
    String targetName,
    ValidationEngine engineSource,
    String explanation,
    String remediation
) {
}

