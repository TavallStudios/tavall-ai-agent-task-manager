package org.tavall.ai.app.loader;

import org.tavall.ai.app.dashboard.DashboardSummaryService;
import org.tavall.ai.app.orchestration.ArtifactService;
import org.tavall.ai.app.orchestration.CleanupReviewService;
import org.tavall.ai.app.orchestration.OverseerOrchestrationService;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import org.tavall.ai.app.orchestration.TaskPoolService;
import org.tavall.ai.app.orchestration.WorkerLifecycleService;
import org.tavall.ai.app.validation.ValidationPipelineService;

public final class ServiceLoaders {

  private static ArtifactService artifactService;
  private static CleanupReviewService cleanupReviewService;
  private static DashboardSummaryService dashboardSummaryService;
  private static OverseerOrchestrationService overseerOrchestrationService;
  private static SharedTaskContextService sharedTaskContextService;
  private static TaskPoolService taskPoolService;
  private static ValidationPipelineService validationPipelineService;
  private static WorkerLifecycleService workerLifecycleService;

  private ServiceLoaders() {
  }

  public static void registerArtifactService(ArtifactService service) {
    artifactService = service;
  }

  public static void registerCleanupReviewService(CleanupReviewService service) {
    cleanupReviewService = service;
  }

  public static void registerDashboardSummaryService(DashboardSummaryService service) {
    dashboardSummaryService = service;
  }

  public static void registerOverseerOrchestrationService(OverseerOrchestrationService service) {
    overseerOrchestrationService = service;
  }

  public static void registerSharedTaskContextService(SharedTaskContextService service) {
    sharedTaskContextService = service;
  }

  public static void registerTaskPoolService(TaskPoolService service) {
    taskPoolService = service;
  }

  public static void registerValidationPipelineService(ValidationPipelineService service) {
    validationPipelineService = service;
  }

  public static void registerWorkerLifecycleService(WorkerLifecycleService service) {
    workerLifecycleService = service;
  }

  public static ArtifactService artifactService() {
    return require(artifactService);
  }

  public static CleanupReviewService cleanupReviewService() {
    return require(cleanupReviewService);
  }

  public static DashboardSummaryService dashboardSummaryService() {
    return require(dashboardSummaryService);
  }

  public static OverseerOrchestrationService overseerOrchestrationService() {
    return require(overseerOrchestrationService);
  }

  public static SharedTaskContextService sharedTaskContextService() {
    return require(sharedTaskContextService);
  }

  public static TaskPoolService taskPoolService() {
    return require(taskPoolService);
  }

  public static ValidationPipelineService validationPipelineService() {
    return require(validationPipelineService);
  }

  public static WorkerLifecycleService workerLifecycleService() {
    return require(workerLifecycleService);
  }

  private static <T> T require(T service) {
    if (service != null) {
      return service;
    }
    throw new IllegalStateException("Requested service has not been registered yet.");
  }
}

