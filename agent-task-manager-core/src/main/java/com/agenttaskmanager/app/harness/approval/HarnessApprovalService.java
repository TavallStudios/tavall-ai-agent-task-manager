package com.agenttaskmanager.app.harness.approval;

import com.agenttaskmanager.app.model.orchestration.CleanupReviewResult;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewTask;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import com.agenttaskmanager.app.orchestration.ArtifactService;
import com.agenttaskmanager.app.orchestration.CleanupReviewService;
import com.agenttaskmanager.app.orchestration.TaskPoolService;
import com.agenttaskmanager.app.persistence.postgres.WorkerTaskRepository;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import com.agenttaskmanager.app.model.validation.ValidationReport;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HarnessApprovalService {

  private final ArtifactService artifactService;
  private final CleanupReviewService cleanupReviewService;
  private final TaskPoolService taskPoolService;
  private final ValidationPipelineService validationPipelineService;
  private final WorkerTaskRepository workerTaskRepository;

  public HarnessApprovalService(
      ArtifactService artifactService,
      CleanupReviewService cleanupReviewService,
      TaskPoolService taskPoolService,
      ValidationPipelineService validationPipelineService,
      WorkerTaskRepository workerTaskRepository
  ) {
    this.artifactService = artifactService;
    this.cleanupReviewService = cleanupReviewService;
    this.taskPoolService = taskPoolService;
    this.validationPipelineService = validationPipelineService;
    this.workerTaskRepository = workerTaskRepository;
  }

  public HarnessApprovalGateResult runApprovalGate(
      String taskId,
      String workerTaskId,
      Path repoPath,
      String diffArtifactId,
      Integer workerExitCode,
      Boolean requiresIntegrationTests
  ) {
    WorkerTask workerTask = workerTaskRepository.getWorkerTask(workerTaskId);
    String diffBody = diffArtifactId == null || diffArtifactId.isBlank()
        ? ""
        : artifactService.readArtifact(diffArtifactId).orElse("");
    HarnessCleanupSummary cleanup = cleanupSummary(taskId, workerTask, diffArtifactId);
    HarnessValidationSummary validation = validationSummary(taskId, workerTask, repoPath);
    boolean patchScopeAllowed = !workerTask.workerType().patchArtifactRequired()
        || validationPipelineService.validatePatchScope(diffBody);
    Map<String, Object> integrationTests = integrationTests(workerTask, repoPath, requiresIntegrationTests);
    int exitCode = workerExitCode == null ? 0 : workerExitCode;
    TaskLifecycleStatus taskStatus = resolveTaskStatus(exitCode, cleanup, validation, integrationTests, patchScopeAllowed);
    String summary = buildSummary(workerTask, exitCode, cleanup, validation, integrationTests, patchScopeAllowed);
    return new HarnessApprovalGateResult(
        taskId,
        workerTaskId,
        taskStatus == TaskLifecycleStatus.COMPLETED,
        taskStatus,
        patchScopeAllowed,
        cleanup,
        validation,
        integrationTests,
        exitCode,
        diffArtifactId,
        summary
    );
  }

  private HarnessCleanupSummary cleanupSummary(String taskId, WorkerTask workerTask, String diffArtifactId) {
    if (!workerTask.workerType().cleanupReviewRequired()
        || diffArtifactId == null
        || diffArtifactId.isBlank()) {
      return new HarnessCleanupSummary(null, "skipped", "Cleanup review skipped for non-code work.", List.of());
    }
    CleanupReviewTask cleanupReviewTask = taskPoolService.createCleanupReviewTask(
        taskId,
        workerTask.workerTaskId(),
        diffArtifactId
    );
    CleanupReviewResult cleanupReviewResult = cleanupReviewService.runCleanupDiffReview(cleanupReviewTask.cleanupReviewId());
    return new HarnessCleanupSummary(
        cleanupReviewResult.cleanupReviewId(),
        cleanupReviewResult.status().name(),
        cleanupReviewResult.summary(),
        cleanupReviewResult.findings()
    );
  }

  private HarnessValidationSummary validationSummary(String taskId, WorkerTask workerTask, Path repoPath) {
    if (!workerTask.workerType().validationRequired()) {
      return new HarnessValidationSummary(null, "skipped", "Validation skipped for non-code work.");
    }
    ValidationReport validationReport = validationPipelineService.runValidationPipeline(
        taskId,
        workerTask.workerTaskId(),
        repoPath
    );
    return new HarnessValidationSummary(
        validationReport.reportId(),
        validationReport.status(),
        validationReport.summary()
    );
  }

  private Map<String, Object> integrationTests(
      WorkerTask workerTask,
      Path repoPath,
      Boolean requiresIntegrationTests
  ) {
    boolean shouldRun = workerTask.workerType().integrationTestsSupported()
        && Boolean.TRUE.equals(resolveIntegrationTests(workerTask, requiresIntegrationTests));
    if (!shouldRun) {
      return Map.of("status", "skipped");
    }
    Map<String, Object> result = new LinkedHashMap<>(validationPipelineService.runIntegrationTests(repoPath));
    result.put("status", ((Number) result.getOrDefault("exitCode", -1)).intValue() == 0 ? "passed" : "failed");
    return result;
  }

  private Boolean resolveIntegrationTests(WorkerTask workerTask, Boolean requiresIntegrationTests) {
    if (requiresIntegrationTests != null) {
      return requiresIntegrationTests;
    }
    return Boolean.TRUE.equals(workerTask.metadata().get("requiresIntegrationTests"));
  }

  private TaskLifecycleStatus resolveTaskStatus(
      int exitCode,
      HarnessCleanupSummary cleanup,
      HarnessValidationSummary validation,
      Map<String, Object> integrationTests,
      boolean patchScopeAllowed
  ) {
    if (exitCode != 0) {
      return TaskLifecycleStatus.FAILED;
    }
    if ("failed".equals(validation.status())) {
      return TaskLifecycleStatus.NEEDS_REWORK;
    }
    if ("NEEDS_REWORK".equals(cleanup.status())) {
      return TaskLifecycleStatus.NEEDS_REWORK;
    }
    if ("failed".equals(String.valueOf(integrationTests.get("status")))) {
      return TaskLifecycleStatus.NEEDS_REWORK;
    }
    if (!patchScopeAllowed) {
      return TaskLifecycleStatus.NEEDS_REWORK;
    }
    return TaskLifecycleStatus.COMPLETED;
  }

  private String buildSummary(
      WorkerTask workerTask,
      int exitCode,
      HarnessCleanupSummary cleanup,
      HarnessValidationSummary validation,
      Map<String, Object> integrationTests,
      boolean patchScopeAllowed
  ) {
    List<String> notes = new java.util.ArrayList<>();
    if (exitCode != 0) {
      notes.add("worker process failed");
    }
    if ("failed".equals(validation.status())) {
      notes.add("validation did not pass");
    }
    if ("NEEDS_REWORK".equals(cleanup.status())) {
      notes.add("cleanup review requires rework");
    }
    if ("failed".equals(String.valueOf(integrationTests.get("status")))) {
      notes.add("integration tests failed");
    }
    if (!patchScopeAllowed) {
      notes.add("patch scope is not allowed");
    }
    String baseSummary = workerTask.workerType().name() + " worker approval gate finished.";
    return notes.isEmpty() ? baseSummary : baseSummary + " Gate result: " + String.join("; ", notes) + ".";
  }
}
