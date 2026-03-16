package com.agenttaskmanager.app.harness.approval;

import java.util.List;

public record HarnessCleanupSummary(
    String cleanupReviewId,
    String status,
    String summary,
    List<String> findings
) {
}
