package com.agenttaskmanager.app.loader;

import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.orchestration.ArtifactService;
import com.agenttaskmanager.app.orchestration.CleanupReviewService;
import com.agenttaskmanager.app.orchestration.OverseerOrchestrationService;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.orchestration.TaskPoolService;
import com.agenttaskmanager.app.orchestration.WorkerLifecycleService;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import org.springframework.stereotype.Component;

@Component
public class ServiceLoaderBootstrap {

  public ServiceLoaderBootstrap(
      ArtifactService artifactService,
      CleanupReviewService cleanupReviewService,
      DashboardSummaryService dashboardSummaryService,
      OverseerOrchestrationService overseerOrchestrationService,
      SharedTaskContextService sharedTaskContextService,
      TaskPoolService taskPoolService,
      ValidationPipelineService validationPipelineService,
      WorkerLifecycleService workerLifecycleService
  ) {
    ServiceLoaders.registerArtifactService(artifactService);
    ServiceLoaders.registerCleanupReviewService(cleanupReviewService);
    ServiceLoaders.registerDashboardSummaryService(dashboardSummaryService);
    ServiceLoaders.registerOverseerOrchestrationService(overseerOrchestrationService);
    ServiceLoaders.registerSharedTaskContextService(sharedTaskContextService);
    ServiceLoaders.registerTaskPoolService(taskPoolService);
    ServiceLoaders.registerValidationPipelineService(validationPipelineService);
    ServiceLoaders.registerWorkerLifecycleService(workerLifecycleService);
  }
}
