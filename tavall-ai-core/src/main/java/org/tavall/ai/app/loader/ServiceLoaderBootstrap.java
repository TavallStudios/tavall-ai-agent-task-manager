package org.tavall.ai.app.loader;

import org.tavall.ai.app.dashboard.DashboardSummaryService;
import org.tavall.ai.app.orchestration.ArtifactService;
import org.tavall.ai.app.orchestration.CleanupReviewService;
import org.tavall.ai.app.orchestration.OverseerOrchestrationService;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import org.tavall.ai.app.orchestration.TaskPoolService;
import org.tavall.ai.app.orchestration.WorkerLifecycleService;
import org.tavall.ai.app.validation.ValidationPipelineService;
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

