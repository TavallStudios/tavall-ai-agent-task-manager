package com.agenttaskmanager.app.harness.approval;

public record HarnessValidationSummary(
    String reportId,
    String status,
    String summary
) {
}
