package org.tavall.ai.app.model.orchestration;

import java.util.List;

public record TaskMergeResult(
    String taskId,
    List<String> workerTaskIds,
    String mergedSummary,
    boolean readyForReview
) {
}

