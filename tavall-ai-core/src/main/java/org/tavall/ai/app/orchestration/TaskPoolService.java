package org.tavall.ai.app.orchestration;

import org.tavall.ai.app.config.OrchestrationProperties;
import org.tavall.ai.app.dashboard.DashboardSummaryService;
import org.tavall.ai.app.harness.routing.HarnessWorkerPlan;
import org.tavall.ai.app.model.orchestration.CleanupReviewTask;
import org.tavall.ai.app.model.orchestration.OverseerTaskBatch;
import org.tavall.ai.app.model.orchestration.TaskAssignment;
import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.model.orchestration.WorkerTask;
import org.tavall.ai.app.model.orchestration.WorkerTransportKind;
import org.tavall.ai.app.model.orchestration.WorkerType;
import org.tavall.ai.app.persistence.postgres.CleanupReviewRepository;
import org.tavall.ai.app.persistence.postgres.TaskBatchRepository;
import org.tavall.ai.app.persistence.postgres.WorkerLeaseRepository;
import org.tavall.ai.app.persistence.postgres.WorkerTaskRepository;
import org.tavall.ai.app.persistence.redis.OrchestrationHotStateStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TaskPoolService {

  private final TaskBatchRepository taskBatchRepository;
  private final WorkerTaskRepository workerTaskRepository;
  private final CleanupReviewRepository cleanupReviewRepository;
  private final DashboardSummaryService dashboardSummaryService;
  private final OrchestrationHotStateStore orchestrationHotStateStore;
  private final OrchestrationProperties orchestrationProperties;
  private final WorkerLeaseRepository workerLeaseRepository;

  public TaskPoolService(
      TaskBatchRepository taskBatchRepository,
      WorkerTaskRepository workerTaskRepository,
      CleanupReviewRepository cleanupReviewRepository,
      DashboardSummaryService dashboardSummaryService,
      OrchestrationHotStateStore orchestrationHotStateStore,
      OrchestrationProperties orchestrationProperties,
      WorkerLeaseRepository workerLeaseRepository
  ) {
    this.taskBatchRepository = taskBatchRepository;
    this.workerTaskRepository = workerTaskRepository;
    this.cleanupReviewRepository = cleanupReviewRepository;
    this.dashboardSummaryService = dashboardSummaryService;
    this.orchestrationHotStateStore = orchestrationHotStateStore;
    this.orchestrationProperties = orchestrationProperties;
    this.workerLeaseRepository = workerLeaseRepository;
  }

  public OverseerTaskBatch createTaskBatch(
      String projectKey,
      String sourceRepo,
      String title,
      boolean multiAgentEnabled,
      List<String> workerRoles
  ) {
    List<HarnessWorkerPlan> workerPlans = workerRoles.stream()
        .map(workerRole -> new HarnessWorkerPlan(
            WorkerType.fromTaskRole(workerRole),
            workerRole,
            workerRole + " worker for " + title,
            WorkerType.fromTaskRole(workerRole).cleanupReviewRequired(),
            WorkerType.fromTaskRole(workerRole).validationRequired(),
            false,
            WorkerType.fromTaskRole(workerRole).patchArtifactRequired()
        ))
        .toList();
    return createPlannedTaskBatch(projectKey, sourceRepo, title, multiAgentEnabled, workerPlans);
  }

  public OverseerTaskBatch createPlannedTaskBatch(
      String projectKey,
      String sourceRepo,
      String title,
      boolean multiAgentEnabled,
      List<HarnessWorkerPlan> workerPlans
  ) {
    OverseerTaskBatch batch = taskBatchRepository.createBatch(
        projectKey,
        sourceRepo,
        title,
        orchestrationProperties.getOverseerAgentId(),
        multiAgentEnabled
    );
    taskBatchRepository.updateStatus(batch.taskId(), TaskLifecycleStatus.QUEUED, batch.overseerAgentId());
    for (HarnessWorkerPlan workerPlan : workerPlans) {
      WorkerTask workerTask = workerTaskRepository.createWorkerTask(
          batch.taskId(),
          null,
          workerPlan.workerType(),
          workerPlan.taskRole(),
          workerPlan.title(),
          3,
          workerPlan.metadata(sourceRepo)
      );
      orchestrationHotStateStore.queueWorkerTask(batch.taskId(), workerTask.workerTaskId());
    }
    dashboardSummaryService.warmDashboardCache();
    return taskBatchRepository.getBatch(batch.taskId());
  }

  public WorkerTask claimWorkerTask(String taskId) {
    return workerTaskRepository.claimNextQueuedTask(taskId).orElse(null);
  }

  public TaskAssignment assignWorkerTask(
      String workerTaskId,
      String agentId,
      WorkerTransportKind transportKind,
      String sessionId
  ) {
    WorkerTask workerTask = workerTaskRepository.getWorkerTask(workerTaskId);
    String leaseToken = "lease_" + UUID.randomUUID();
    workerLeaseRepository.assignWorkerTask(
        workerTaskId,
        agentId,
        transportKind,
        sessionId,
        leaseToken,
        orchestrationProperties.getLeaseDurationSeconds()
    );
    orchestrationHotStateStore.recordWorkerHeartbeat(
        workerTaskId,
        agentId,
        Duration.ofSeconds(orchestrationProperties.getLeaseDurationSeconds())
    );
    orchestrationHotStateStore.recordWorkerStatus(workerTaskId, TaskLifecycleStatus.ASSIGNED);
    dashboardSummaryService.warmDashboardCache();
    return new TaskAssignment(workerTask.taskId(), workerTaskId, agentId, transportKind, leaseToken);
  }

  public WorkerTask reassignWorkerTask(String workerTaskId, String summary) {
    WorkerTask workerTask = workerTaskRepository.getWorkerTask(workerTaskId);
    workerTaskRepository.reassignWorkerTask(workerTaskId, summary);
    orchestrationHotStateStore.queueWorkerTask(workerTask.taskId(), workerTaskId);
    orchestrationHotStateStore.incrementCounter("reassignments");
    dashboardSummaryService.warmDashboardCache();
    return workerTaskRepository.getWorkerTask(workerTaskId);
  }

  public WorkerTask completeWorkerTask(String workerTaskId, String summary) {
    workerTaskRepository.updateWorkerTaskStatus(workerTaskId, TaskLifecycleStatus.COMPLETED, summary);
    workerLeaseRepository.deleteLease(workerTaskId);
    orchestrationHotStateStore.recordWorkerStatus(workerTaskId, TaskLifecycleStatus.COMPLETED);
    dashboardSummaryService.warmDashboardCache();
    return workerTaskRepository.getWorkerTask(workerTaskId);
  }

  public WorkerTask approveWorkerTask(String workerTaskId, String summary) {
    workerTaskRepository.updateWorkerTaskStatus(workerTaskId, TaskLifecycleStatus.APPROVED, summary);
    workerLeaseRepository.deleteLease(workerTaskId);
    orchestrationHotStateStore.recordWorkerStatus(workerTaskId, TaskLifecycleStatus.APPROVED);
    dashboardSummaryService.warmDashboardCache();
    return workerTaskRepository.getWorkerTask(workerTaskId);
  }

  public WorkerTask markWorkerNeedsRework(String workerTaskId, String summary) {
    workerTaskRepository.updateWorkerTaskStatus(workerTaskId, TaskLifecycleStatus.NEEDS_REWORK, summary);
    workerLeaseRepository.deleteLease(workerTaskId);
    orchestrationHotStateStore.recordWorkerStatus(workerTaskId, TaskLifecycleStatus.NEEDS_REWORK);
    orchestrationHotStateStore.incrementCounter("needs-rework");
    dashboardSummaryService.warmDashboardCache();
    return workerTaskRepository.getWorkerTask(workerTaskId);
  }

  public WorkerTask failWorkerTask(String workerTaskId, String summary) {
    workerTaskRepository.updateWorkerTaskStatus(workerTaskId, TaskLifecycleStatus.FAILED, summary);
    workerLeaseRepository.deleteLease(workerTaskId);
    orchestrationHotStateStore.recordWorkerStatus(workerTaskId, TaskLifecycleStatus.FAILED);
    orchestrationHotStateStore.incrementCounter("worker-failures");
    dashboardSummaryService.warmDashboardCache();
    return workerTaskRepository.getWorkerTask(workerTaskId);
  }

  public WorkerTask deadLetterWorkerTask(String workerTaskId, String summary) {
    workerTaskRepository.updateWorkerTaskStatus(workerTaskId, TaskLifecycleStatus.DEAD_LETTER, summary);
    workerLeaseRepository.deleteLease(workerTaskId);
    orchestrationHotStateStore.recordWorkerStatus(workerTaskId, TaskLifecycleStatus.DEAD_LETTER);
    orchestrationHotStateStore.incrementCounter("dead-letter");
    dashboardSummaryService.warmDashboardCache();
    return workerTaskRepository.getWorkerTask(workerTaskId);
  }

  public CleanupReviewTask createCleanupReviewTask(
      String taskId,
      String workerTaskId,
      String diffArtifactId
  ) {
    CleanupReviewTask reviewTask = cleanupReviewRepository.createReviewTask(
        taskId,
        workerTaskId,
        orchestrationProperties.getCleanupAgentId(),
        diffArtifactId
    );
    dashboardSummaryService.warmDashboardCache();
    return reviewTask;
  }

  public List<WorkerTask> listWorkerTasks(String taskId) {
    return workerTaskRepository.listWorkerTasks(taskId);
  }
}

