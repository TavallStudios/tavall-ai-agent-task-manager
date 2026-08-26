package org.tavall.ai.app.harness.state;

import org.tavall.ai.app.dashboard.DashboardSummaryService;
import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.model.orchestration.WorkerTask;
import org.tavall.ai.app.persistence.postgres.CleanupReviewRepository;
import org.tavall.ai.app.persistence.postgres.OverseerDecisionRepository;
import org.tavall.ai.app.persistence.postgres.PatchDecisionRepository;
import org.tavall.ai.app.persistence.postgres.TaskBatchRepository;
import org.tavall.ai.app.persistence.postgres.ValidationReportRepository;
import org.tavall.ai.app.persistence.postgres.WorkerCheckInRepository;
import org.tavall.ai.app.persistence.postgres.WorkerLeaseRepository;
import org.tavall.ai.app.persistence.postgres.WorkerTaskRepository;
import org.tavall.ai.app.persistence.redis.OrchestrationHotStateStore;
import org.tavall.ai.app.orchestration.ArtifactService;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import org.tavall.ai.app.retrieval.SemanticMemoryService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.tavall.ai.app.validation.ValidationPipelineService;

@Service
public class HarnessStateService {

  private final ArtifactService artifactService;
  private final CleanupReviewRepository cleanupReviewRepository;
  private final DashboardSummaryService dashboardSummaryService;
  private final OverseerDecisionRepository overseerDecisionRepository;
  private final OrchestrationHotStateStore orchestrationHotStateStore;
  private final PatchDecisionRepository patchDecisionRepository;
  private final SharedTaskContextService sharedTaskContextService;
  private final SemanticMemoryService semanticMemoryService;
  private final TaskBatchRepository taskBatchRepository;
  private final ValidationReportRepository validationReportRepository;
  private final ValidationPipelineService validationPipelineService;
  private final WorkerCheckInRepository workerCheckInRepository;
  private final WorkerLeaseRepository workerLeaseRepository;
  private final WorkerTaskRepository workerTaskRepository;

  public HarnessStateService(
      ArtifactService artifactService,
      CleanupReviewRepository cleanupReviewRepository,
      DashboardSummaryService dashboardSummaryService,
      OverseerDecisionRepository overseerDecisionRepository,
      OrchestrationHotStateStore orchestrationHotStateStore,
      PatchDecisionRepository patchDecisionRepository,
      SharedTaskContextService sharedTaskContextService,
      SemanticMemoryService semanticMemoryService,
      TaskBatchRepository taskBatchRepository,
      ValidationReportRepository validationReportRepository,
      ValidationPipelineService validationPipelineService,
      WorkerCheckInRepository workerCheckInRepository,
      WorkerLeaseRepository workerLeaseRepository,
      WorkerTaskRepository workerTaskRepository
  ) {
    this.artifactService = artifactService;
    this.cleanupReviewRepository = cleanupReviewRepository;
    this.dashboardSummaryService = dashboardSummaryService;
    this.overseerDecisionRepository = overseerDecisionRepository;
    this.orchestrationHotStateStore = orchestrationHotStateStore;
    this.patchDecisionRepository = patchDecisionRepository;
    this.sharedTaskContextService = sharedTaskContextService;
    this.semanticMemoryService = semanticMemoryService;
    this.taskBatchRepository = taskBatchRepository;
    this.validationReportRepository = validationReportRepository;
    this.validationPipelineService = validationPipelineService;
    this.workerCheckInRepository = workerCheckInRepository;
    this.workerLeaseRepository = workerLeaseRepository;
    this.workerTaskRepository = workerTaskRepository;
  }

  public HarnessStateSnapshot loadState(String taskId) {
    List<WorkerTask> workerTasks = workerTaskRepository.listWorkerTasks(taskId);
    Map<String, Map<String, Object>> cachedValidationSummaries = new LinkedHashMap<>();
    workerTasks.stream()
        .map(WorkerTask::workerTaskId)
        .forEach(workerTaskId -> cachedValidationSummaries.put(
            workerTaskId,
            validationPipelineService.getCachedValidationSummary(taskId, workerTaskId)
        ));
    HarnessTaskSchema taskSchema = new HarnessTaskSchema(
        taskBatchRepository.getBatch(taskId),
        workerTasks,
        sharedTaskContextService.listSharedTaskContext(taskId),
        artifactService.loadTaskArtifacts(taskId, null),
        cleanupReviewRepository.listByTask(taskId),
        validationReportRepository.listReportsByTask(taskId),
        overseerDecisionRepository.listByTask(taskId),
        patchDecisionRepository.listByTask(taskId)
    );
    HarnessAgentSchema agentSchema = new HarnessAgentSchema(
        workerLeaseRepository.listByTask(taskId),
        workerCheckInRepository.listByTask(taskId),
        workerTasks.stream()
            .map(workerTask -> new HarnessWorkerSummary(
                workerTask.workerTaskId(),
                workerTask.workerType(),
                workerTask.taskRole(),
                workerTask.status(),
                workerTask.assignedAgentId(),
                isDead(workerTask.status()),
                orchestrationHotStateStore.getWorkerStatus(workerTask.workerTaskId())
            ))
            .toList()
    );
    HarnessPersistenceModel persistenceModel = new HarnessPersistenceModel(
        orchestrationHotStateStore.workerQueueDepth(taskId),
        sharedTaskContextService.loadTaskContext(taskId),
        cachedValidationSummaries,
        semanticMemoryService.searchProject(
            taskSchema.batch().projectKey(),
            taskSchema.batch().title(),
            5,
            Map.of()
        ),
        storeCounts(taskSchema, agentSchema)
    );
    HarnessDashboardModel dashboardModel = new HarnessDashboardModel(
        dashboardSummaryService.loadDashboardSummary(),
        countBy(workerTasks, workerTask -> workerTask.workerType().name()),
        countBy(workerTasks, workerTask -> workerTask.status().name())
    );
    return new HarnessStateSnapshot(taskSchema, agentSchema, persistenceModel, dashboardModel);
  }

  private Map<String, Object> storeCounts(HarnessTaskSchema taskSchema, HarnessAgentSchema agentSchema) {
    Map<String, Object> storeCounts = new LinkedHashMap<>();
    storeCounts.put("postgresWorkerTasks", taskSchema.workerTasks().size());
    storeCounts.put("postgresCleanupReviews", taskSchema.cleanupReviews().size());
    storeCounts.put("postgresValidationReports", taskSchema.validationReports().size());
    storeCounts.put("postgresOverseerDecisions", taskSchema.overseerDecisions().size());
    storeCounts.put("mongoArtifacts", taskSchema.taskArtifacts().size());
    storeCounts.put("redisLeases", agentSchema.activeLeases().size());
    storeCounts.put("redisCheckIns", agentSchema.workerCheckIns().size());
    storeCounts.put("sharedContextEntries", taskSchema.sharedTaskContext().size());
    return storeCounts;
  }

  private Map<String, Long> countBy(List<WorkerTask> workerTasks, Function<WorkerTask, String> classifier) {
    return workerTasks.stream()
        .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.counting()));
  }

  private boolean isDead(TaskLifecycleStatus status) {
    return status == TaskLifecycleStatus.DEAD || status == TaskLifecycleStatus.DEAD_LETTER;
  }
}
