package com.agenttaskmanager.app.model.orchestration;

public record WorkerExecutionResult(
    WorkerRunSummary runSummary,
    String outputArtifactId,
    String diffArtifactId,
    String cleanupReviewId,
    String validationReportId,
    boolean patchScopeAllowed,
    TaskLifecycleStatus cleanupStatus,
    String validationStatus,
    int exitCode
) {
}
