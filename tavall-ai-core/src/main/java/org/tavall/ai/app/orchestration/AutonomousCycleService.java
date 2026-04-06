package org.tavall.ai.app.orchestration;

import org.tavall.ai.app.config.OrchestrationProperties;
import org.tavall.ai.app.dashboard.DashboardSummaryService;
import org.tavall.ai.app.model.orchestration.AutonomousCycleReport;
import org.tavall.ai.app.model.orchestration.DeadWorkerRecord;
import org.tavall.ai.app.model.orchestration.OverseerTaskBatch;
import org.tavall.ai.app.model.orchestration.PatchDecisionRecord;
import org.tavall.ai.app.model.orchestration.TaskAssignment;
import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.model.orchestration.TaskMergeResult;
import org.tavall.ai.app.model.orchestration.WorkerExecutionRequest;
import org.tavall.ai.app.model.orchestration.WorkerExecutionResult;
import org.tavall.ai.app.model.orchestration.WorkerTask;
import org.tavall.ai.app.model.orchestration.WorkerTransportKind;
import org.tavall.ai.app.persistence.postgres.TaskBatchRepository;
import org.tavall.ai.app.persistence.postgres.WorkerTaskRepository;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AutonomousCycleService {

  private final DashboardSummaryService dashboardSummaryService;
  private final LocalCodexWorkerTransport localCodexWorkerTransport;
  private final OrchestrationProperties orchestrationProperties;
  private final OverseerOrchestrationService overseerOrchestrationService;
  private final TaskBatchRepository taskBatchRepository;
  private final TaskPoolService taskPoolService;
  private final WorkerTaskRepository workerTaskRepository;

  public AutonomousCycleService(
      DashboardSummaryService dashboardSummaryService,
      LocalCodexWorkerTransport localCodexWorkerTransport,
      OrchestrationProperties orchestrationProperties,
      OverseerOrchestrationService overseerOrchestrationService,
      TaskBatchRepository taskBatchRepository,
      TaskPoolService taskPoolService,
      WorkerTaskRepository workerTaskRepository
  ) {
    this.dashboardSummaryService = dashboardSummaryService;
    this.localCodexWorkerTransport = localCodexWorkerTransport;
    this.orchestrationProperties = orchestrationProperties;
    this.overseerOrchestrationService = overseerOrchestrationService;
    this.taskBatchRepository = taskBatchRepository;
    this.taskPoolService = taskPoolService;
    this.workerTaskRepository = workerTaskRepository;
  }

  public AutonomousCycleReport runCycle(Path fallbackRepoPath) {
    List<DeadWorkerRecord> deadWorkers = overseerOrchestrationService.detectDeadWorkers();
    List<String> processedBatchIds = new ArrayList<>();
    List<String> completedBatchIds = new ArrayList<>();
    List<String> failedBatchIds = new ArrayList<>();
    List<String> patchDecisionIds = new ArrayList<>();
    int workerRuns = 0;

    for (OverseerTaskBatch batch : openBatches()) {
      processedBatchIds.add(batch.taskId());
      workerRuns += processBatch(batch, fallbackRepoPath.toAbsolutePath(), patchDecisionIds);
      TaskLifecycleStatus batchStatus = reconcileBatch(batch.taskId());

      if (batchStatus == TaskLifecycleStatus.COMPLETED) {
        completedBatchIds.add(batch.taskId());
      } else if (batchStatus == TaskLifecycleStatus.FAILED) {
        failedBatchIds.add(batch.taskId());
      }

      if (workerRuns >= orchestrationProperties.getAutonomyMaxWorkerRunsPerCycle()) {
        break;
      }
    }

    return new AutonomousCycleReport(
        workerRuns,
        deadWorkers,
        processedBatchIds,
        completedBatchIds,
        failedBatchIds,
        patchDecisionIds,
        dashboardSummaryService.warmDashboardCache(),
        OffsetDateTime.now()
    );
  }

  private List<OverseerTaskBatch> openBatches() {
    return taskBatchRepository.listBatches(orchestrationProperties.getAutonomyMaxBatchCountPerCycle()).stream()
        .filter(batch -> batch.status() != TaskLifecycleStatus.COMPLETED && batch.status() != TaskLifecycleStatus.FAILED)
        .toList();
  }

  private int processBatch(OverseerTaskBatch batch, Path fallbackRepoPath, List<String> patchDecisionIds) {
    int workerRuns = 0;
    while (workerRuns < orchestrationProperties.getAutonomyMaxWorkerRunsPerCycle()) {
      String agentId = "autonomy-worker-" + UUID.randomUUID();
      String sessionId = "autonomy-session-" + UUID.randomUUID();
      TaskAssignment assignment = overseerOrchestrationService.assignNextWorkerTask(
          batch.taskId(),
          agentId,
          WorkerTransportKind.LOCAL_CODEX_EXEC,
          sessionId
      );

      if (assignment == null) {
        return workerRuns;
      }

      Path repoPath = resolveRepoPath(batch, fallbackRepoPath);
      WorkerExecutionResult executionResult = localCodexWorkerTransport.executeWorkerTask(
          new WorkerExecutionRequest(batch.taskId(), assignment.workerTaskId(), agentId, sessionId, repoPath)
      );
      PatchDecisionRecord patchDecision = finalizeWorkerExecution(batch.taskId(), executionResult);
      patchDecisionIds.add(patchDecision.patchDecisionId());
      workerRuns++;
    }
    return workerRuns;
  }

  private PatchDecisionRecord finalizeWorkerExecution(String taskId, WorkerExecutionResult executionResult) {
    boolean approved = executionResult.exitCode() == 0
        && executionResult.patchScopeAllowed()
        && executionResult.cleanupStatus() == TaskLifecycleStatus.APPROVED
        && "passed".equals(executionResult.validationStatus());
    String workerTaskId = executionResult.runSummary().workerTaskId();
    PatchDecisionRecord patchDecision = overseerOrchestrationService.decidePatch(
        taskId,
        workerTaskId,
        executionResult.validationReportId(),
        executionResult.cleanupReviewId(),
        executionResult.diffArtifactId(),
        approved
    );
    WorkerTask workerTask = workerTaskRepository.getWorkerTask(workerTaskId);

    if (approved) {
      taskPoolService.approveWorkerTask(workerTaskId, executionResult.runSummary().summary());
      overseerOrchestrationService.storeOverseerDecision(
          taskId,
          workerTaskId,
          "autonomy-approve",
          TaskLifecycleStatus.APPROVED,
          "Approved worker output after cleanup and validation.",
          Map.of("patchDecisionId", patchDecision.patchDecisionId())
      );
      return patchDecision;
    }

    if (workerTask.attemptCount() >= workerTask.maxAttempts()) {
      taskPoolService.deadLetterWorkerTask(workerTaskId, executionResult.runSummary().summary());
      overseerOrchestrationService.storeOverseerDecision(
          taskId,
          workerTaskId,
          "autonomy-dead-letter",
          TaskLifecycleStatus.DEAD_LETTER,
          "Moved worker task to the dead-letter state after retry exhaustion.",
          Map.of("patchDecisionId", patchDecision.patchDecisionId())
      );
      return patchDecision;
    }

    taskPoolService.reassignWorkerTask(workerTaskId, executionResult.runSummary().summary());
    overseerOrchestrationService.storeOverseerDecision(
        taskId,
        workerTaskId,
        "autonomy-reassign",
        TaskLifecycleStatus.REASSIGNED,
        "Requeued worker task after a failed autonomy gate.",
        Map.of("patchDecisionId", patchDecision.patchDecisionId())
    );
    return patchDecision;
  }

  private TaskLifecycleStatus reconcileBatch(String taskId) {
    OverseerTaskBatch batch = taskBatchRepository.getBatch(taskId);
    List<WorkerTask> workerTasks = taskPoolService.listWorkerTasks(taskId);

    if (workerTasks.isEmpty()) {
      return batch.status();
    }

    boolean hasQueuedWork = workerTasks.stream().anyMatch(this::isQueuedWork);
    boolean hasActiveWork = workerTasks.stream().anyMatch(this::isActiveWork);
    boolean hasFailedWork = workerTasks.stream().anyMatch(this::isFailedWork);
    boolean allApproved = workerTasks.stream().allMatch(workerTask -> workerTask.status() == TaskLifecycleStatus.APPROVED);

    if (allApproved) {
      updateBatchStatus(batch, TaskLifecycleStatus.COMPLETED);
      TaskMergeResult mergeResult = overseerOrchestrationService.mergeWorkerOutputs(taskId);
      overseerOrchestrationService.storeRunSummary(taskId, mergeResult.mergedSummary());
      return TaskLifecycleStatus.COMPLETED;
    }

    if (hasActiveWork) {
      updateBatchStatus(batch, TaskLifecycleStatus.RUNNING);
      return TaskLifecycleStatus.RUNNING;
    }

    if (hasQueuedWork) {
      updateBatchStatus(batch, TaskLifecycleStatus.QUEUED);
      return TaskLifecycleStatus.QUEUED;
    }

    if (hasFailedWork) {
      updateBatchStatus(batch, TaskLifecycleStatus.FAILED);
      overseerOrchestrationService.storeOverseerDecision(
          taskId,
          null,
          "autonomy-batch-failed",
          TaskLifecycleStatus.FAILED,
          "Autonomous cycle exhausted the worker retries for this batch.",
          Map.of()
      );
      return TaskLifecycleStatus.FAILED;
    }

    return batch.status();
  }

  private void updateBatchStatus(OverseerTaskBatch batch, TaskLifecycleStatus status) {
    if (batch.status() != status) {
      taskBatchRepository.updateStatus(batch.taskId(), status, batch.overseerAgentId());
    }
  }

  private boolean isQueuedWork(WorkerTask workerTask) {
    return workerTask.status() == TaskLifecycleStatus.QUEUED
        || workerTask.status() == TaskLifecycleStatus.REASSIGNED
        || workerTask.status() == TaskLifecycleStatus.NEEDS_REWORK;
  }

  private boolean isActiveWork(WorkerTask workerTask) {
    return workerTask.status() == TaskLifecycleStatus.ASSIGNED
        || workerTask.status() == TaskLifecycleStatus.RUNNING
        || workerTask.status() == TaskLifecycleStatus.CHECKED_IN
        || workerTask.status() == TaskLifecycleStatus.UNDER_REVIEW
        || workerTask.status() == TaskLifecycleStatus.BLOCKED;
  }

  private boolean isFailedWork(WorkerTask workerTask) {
    return workerTask.status() == TaskLifecycleStatus.FAILED
        || workerTask.status() == TaskLifecycleStatus.DEAD
        || workerTask.status() == TaskLifecycleStatus.DEAD_LETTER;
  }

  private Path resolveRepoPath(OverseerTaskBatch batch, Path fallbackRepoPath) {
    if (batch.sourceRepo() != null && !batch.sourceRepo().isBlank()) {
      return Path.of(batch.sourceRepo()).toAbsolutePath();
    }
    return fallbackRepoPath;
  }
}

