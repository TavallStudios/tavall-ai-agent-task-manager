package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.model.orchestration.CleanupReviewResult;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewTask;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.persistence.postgres.CleanupReviewRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CleanupReviewService {

  private final CleanupReviewRepository cleanupReviewRepository;
  private final ArtifactService artifactService;

  public CleanupReviewService(
      CleanupReviewRepository cleanupReviewRepository,
      ArtifactService artifactService
  ) {
    this.cleanupReviewRepository = cleanupReviewRepository;
    this.artifactService = artifactService;
  }

  public CleanupReviewTask createCleanupReviewTask(String taskId, String workerTaskId, String diffArtifactId) {
    return cleanupReviewRepository.createReviewTask(taskId, workerTaskId, null, diffArtifactId);
  }

  public CleanupReviewResult runCleanupDiffReview(String cleanupReviewId) {
    CleanupReviewTask reviewTask = cleanupReviewRepository.getReviewTask(cleanupReviewId);
    String diff = artifactService.readArtifact(reviewTask.diffArtifactId()).orElse("");
    List<String> findings = new ArrayList<>();

    if (diff.contains("TODO") || diff.contains("FIXME")) {
      findings.add("Remove TODO/FIXME markers from proposed changes before approval.");
    }
    if (diff.contains("System.out.println(") || diff.contains("printStackTrace(")) {
      findings.add("Remove debugging output from the diff before approval.");
    }
    if (diff.contains("@Mock") || diff.contains("mock(")) {
      findings.add("Do not add mocked unit-test patterns to integration-only validation flow.");
    }
    if (diff.contains("<<<<<<<") || diff.contains(">>>>>>>")) {
      findings.add("Resolve merge markers before approval.");
    }

    TaskLifecycleStatus status = findings.isEmpty()
        ? TaskLifecycleStatus.APPROVED
        : TaskLifecycleStatus.NEEDS_REWORK;
    String summary = findings.isEmpty()
        ? "Cleanup review approved the diff."
        : "Cleanup review requires rework.";
    return cleanupReviewRepository.completeReview(cleanupReviewId, status, summary, findings);
  }

  public CleanupReviewResult submitCleanupReview(
      String cleanupReviewId,
      TaskLifecycleStatus status,
      String summary,
      List<String> findings
  ) {
    return cleanupReviewRepository.completeReview(cleanupReviewId, status, summary, findings);
  }

  public CleanupReviewResult markCleanupReviewRequired(String cleanupReviewId, String reason) {
    return cleanupReviewRepository.completeReview(
        cleanupReviewId,
        TaskLifecycleStatus.UNDER_REVIEW,
        "Cleanup review is required before approval.",
        List.of(reason)
    );
  }

  public CleanupReviewResult markCleanupApproved(String cleanupReviewId, String summary) {
    return cleanupReviewRepository.completeReview(cleanupReviewId, TaskLifecycleStatus.APPROVED, summary, List.of());
  }

  public CleanupReviewResult markCleanupRejected(String cleanupReviewId, String summary, List<String> findings) {
    return cleanupReviewRepository.completeReview(cleanupReviewId, TaskLifecycleStatus.NEEDS_REWORK, summary, findings);
  }
}
