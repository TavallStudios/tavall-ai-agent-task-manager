package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.config.OrchestrationProperties;
import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.harness.routing.HarnessWorkerPlan;
import com.agenttaskmanager.app.model.orchestration.DeadWorkerRecord;
import com.agenttaskmanager.app.model.orchestration.OverseerDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.OverseerTaskBatch;
import com.agenttaskmanager.app.model.orchestration.PatchDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.TaskAssignment;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.TaskMergeResult;
import com.agenttaskmanager.app.model.orchestration.WorkerLease;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
import com.agenttaskmanager.app.persistence.postgres.OverseerDecisionRepository;
import com.agenttaskmanager.app.persistence.postgres.PatchDecisionRepository;
import com.agenttaskmanager.app.persistence.postgres.WorkerLeaseRepository;
import com.agenttaskmanager.app.persistence.postgres.WorkerTaskRepository;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import com.agenttaskmanager.app.model.validation.ValidationReport;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OverseerOrchestrationService {

  private final TaskPoolService taskPoolService;
  private final WorkerLifecycleService workerLifecycleService;
  private final ArtifactService artifactService;
  private final CleanupReviewService cleanupReviewService;
  private final DashboardSummaryService dashboardSummaryService;
  private final ValidationPipelineService validationPipelineService;
  private final WorkerLeaseRepository workerLeaseRepository;
  private final WorkerTaskRepository workerTaskRepository;
  private final OverseerDecisionRepository overseerDecisionRepository;
  private final PatchDecisionRepository patchDecisionRepository;
  private final OrchestrationProperties orchestrationProperties;

  public OverseerOrchestrationService(
      TaskPoolService taskPoolService,
      WorkerLifecycleService workerLifecycleService,
      ArtifactService artifactService,
      CleanupReviewService cleanupReviewService,
      DashboardSummaryService dashboardSummaryService,
      ValidationPipelineService validationPipelineService,
      WorkerLeaseRepository workerLeaseRepository,
      WorkerTaskRepository workerTaskRepository,
      OverseerDecisionRepository overseerDecisionRepository,
      PatchDecisionRepository patchDecisionRepository,
      OrchestrationProperties orchestrationProperties
  ) {
    this.taskPoolService = taskPoolService;
    this.workerLifecycleService = workerLifecycleService;
    this.artifactService = artifactService;
    this.cleanupReviewService = cleanupReviewService;
    this.dashboardSummaryService = dashboardSummaryService;
    this.validationPipelineService = validationPipelineService;
    this.workerLeaseRepository = workerLeaseRepository;
    this.workerTaskRepository = workerTaskRepository;
    this.overseerDecisionRepository = overseerDecisionRepository;
    this.patchDecisionRepository = patchDecisionRepository;
    this.orchestrationProperties = orchestrationProperties;
  }

  public OverseerTaskBatch createTaskBatch(
      String projectKey,
      String sourceRepo,
      String title,
      boolean multiAgentEnabled,
      List<String> workerRoles
  ) {
    OverseerTaskBatch batch = taskPoolService.createTaskBatch(
        projectKey,
        sourceRepo,
        title,
        multiAgentEnabled,
        workerRoles
    );
    overseerDecisionRepository.storeDecision(
        batch.taskId(),
        null,
        "create-batch",
        TaskLifecycleStatus.QUEUED,
        "Created orchestration batch with " + workerRoles.size() + " worker tasks.",
        Map.of("workerRoles", workerRoles)
    );
    return batch;
  }

  public OverseerTaskBatch createPlannedTaskBatch(
      String projectKey,
      String sourceRepo,
      String title,
      boolean multiAgentEnabled,
      List<HarnessWorkerPlan> workerPlans,
      Map<String, Object> details
  ) {
    OverseerTaskBatch batch = taskPoolService.createPlannedTaskBatch(
        projectKey,
        sourceRepo,
        title,
        multiAgentEnabled,
        workerPlans
    );
    overseerDecisionRepository.storeDecision(
        batch.taskId(),
        null,
        "create-batch",
        TaskLifecycleStatus.QUEUED,
        "Created harness batch with " + workerPlans.size() + " worker tasks.",
        Map.of(
            "workerTypes",
            workerPlans.stream().map(plan -> plan.workerType().name()).toList(),
            "details",
            details
        )
    );
    return batch;
  }

  public TaskAssignment assignNextWorkerTask(
      String taskId,
      String agentId,
      WorkerTransportKind transportKind,
      String sessionId
  ) {
    WorkerTask workerTask = taskPoolService.claimWorkerTask(taskId);
    if (workerTask == null) {
      return null;
    }
    workerLifecycleService.registerWorker(sessionId, agentId, "local-host", "AgentTaskManager", "", transportKind);
    TaskAssignment assignment = taskPoolService.assignWorkerTask(workerTask.workerTaskId(), agentId, transportKind, sessionId);
    overseerDecisionRepository.storeDecision(
        taskId,
        workerTask.workerTaskId(),
        "assign-worker",
        TaskLifecycleStatus.ASSIGNED,
        "Assigned worker task to " + agentId,
        Map.of("transportKind", transportKind.name())
    );
    return assignment;
  }

  public List<DeadWorkerRecord> detectDeadWorkers() {
    List<WorkerLease> expiredLeases = workerLeaseRepository.findExpiredLeases();
    return expiredLeases.stream()
        .map(lease -> {
          DeadWorkerRecord deadWorkerRecord = workerLifecycleService.markWorkerDead(
              lease.workerTaskId(),
              "Worker lease expired."
          );
          taskPoolService.reassignWorkerTask(lease.workerTaskId(), "Reassigned after worker timeout.");
          overseerDecisionRepository.storeDecision(
              lease.taskId(),
              lease.workerTaskId(),
              "dead-worker-detected",
              TaskLifecycleStatus.REASSIGNED,
              "Detected dead worker and requeued the task.",
              Map.of("agentId", lease.agentId(), "expiresAt", String.valueOf(lease.expiresAt()))
          );
          return deadWorkerRecord;
        })
        .toList();
  }

  public TaskMergeResult mergeWorkerOutputs(String taskId) {
    List<WorkerTask> workerTasks = taskPoolService.listWorkerTasks(taskId);
    String mergedSummary = workerTasks.stream()
        .map(workerTask -> workerTask.taskRole() + ": " + (workerTask.latestSummary() == null ? "" : workerTask.latestSummary()))
        .reduce((left, right) -> left + "\n" + right)
        .orElse("No worker output was recorded.");
    List<String> completedWorkerTaskIds = workerTasks.stream()
        .filter(workerTask -> workerTask.status() == TaskLifecycleStatus.COMPLETED
            || workerTask.status() == TaskLifecycleStatus.APPROVED)
        .map(WorkerTask::workerTaskId)
        .toList();
    return new TaskMergeResult(taskId, completedWorkerTaskIds, mergedSummary, !completedWorkerTaskIds.isEmpty());
  }

  public ValidationReport validateBatch(String taskId, String workerTaskId, Path repoPath) {
    return validationPipelineService.runValidationPipeline(taskId, workerTaskId, repoPath);
  }

  public PatchDecisionRecord decidePatch(
      String taskId,
      String workerTaskId,
      String validationReportId,
      String cleanupReviewId,
      String diffArtifactId,
      boolean approved
  ) {
    TaskLifecycleStatus status = approved ? TaskLifecycleStatus.APPROVED : TaskLifecycleStatus.NEEDS_REWORK;
    String summary = approved
        ? "Patch approved by overseer."
        : "Patch rejected by overseer.";

    if (workerTaskId != null && !workerTaskId.isBlank()) {
      if (approved) {
        taskPoolService.approveWorkerTask(workerTaskId, summary);
      } else {
        taskPoolService.markWorkerNeedsRework(workerTaskId, summary);
      }
    }

    PatchDecisionRecord patchDecision = patchDecisionRepository.storeDecision(
        taskId,
        workerTaskId,
        validationReportId,
        cleanupReviewId,
        diffArtifactId,
        status,
        summary,
        orchestrationProperties.getOverseerAgentId(),
        Map.of("approved", approved)
    );
    overseerDecisionRepository.storeDecision(
        taskId,
        workerTaskId,
        "patch-gate",
        status,
        summary,
        Map.of("patchDecisionId", patchDecision.patchDecisionId())
    );
    dashboardSummaryService.warmDashboardCache();
    return patchDecision;
  }

  public OverseerDecisionRecord storeOverseerDecision(
      String taskId,
      String workerTaskId,
      String decisionType,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> details
  ) {
    return overseerDecisionRepository.storeDecision(taskId, workerTaskId, decisionType, status, summary, details);
  }

  public OverseerDecisionRecord storeRunSummary(String taskId, String summary) {
    return storeOverseerDecision(
        taskId,
        null,
        "run-summary",
        TaskLifecycleStatus.COMPLETED,
        summary,
        Map.of()
    );
  }
}
