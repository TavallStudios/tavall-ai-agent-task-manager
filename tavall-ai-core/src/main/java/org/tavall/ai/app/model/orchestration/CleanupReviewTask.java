package org.tavall.ai.app.model.orchestration;

public record CleanupReviewTask(
    String cleanupReviewId,
    String taskId,
    String workerTaskId,
    String diffArtifactId,
    TaskLifecycleStatus status
) {
}

