package com.agenttaskmanager.app.harness.intake;

import com.agenttaskmanager.app.harness.routing.HarnessRoutingPlan;
import com.agenttaskmanager.app.harness.routing.HarnessRoutingService;
import com.agenttaskmanager.app.harness.state.HarnessStateService;
import com.agenttaskmanager.app.harness.state.HarnessStateSnapshot;
import com.agenttaskmanager.app.model.KnownRepo;
import com.agenttaskmanager.app.orchestration.OverseerOrchestrationService;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.service.RepoCatalogService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HarnessTaskIntakeService {

  private final HarnessRoutingService harnessRoutingService;
  private final HarnessStateService harnessStateService;
  private final OverseerOrchestrationService overseerOrchestrationService;
  private final RepoCatalogService repoCatalogService;
  private final SharedTaskContextService sharedTaskContextService;

  public HarnessTaskIntakeService(
      HarnessRoutingService harnessRoutingService,
      HarnessStateService harnessStateService,
      OverseerOrchestrationService overseerOrchestrationService,
      RepoCatalogService repoCatalogService,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.harnessRoutingService = harnessRoutingService;
    this.harnessStateService = harnessStateService;
    this.overseerOrchestrationService = overseerOrchestrationService;
    this.repoCatalogService = repoCatalogService;
    this.sharedTaskContextService = sharedTaskContextService;
  }

  public HarnessStateSnapshot intakeTask(ParentTaskRequest request) {
    Path repoPath = resolveRepoPath(request.repoRef());
    KnownRepo repo = repoCatalogService.requireByPath(repoPath.toString());
    HarnessRoutingPlan routingPlan = harnessRoutingService.routeTask(request);
    String taskId = overseerOrchestrationService.createPlannedTaskBatch(
        repo.projectKey(),
        repo.repoPath(),
        request.title(),
        request.multiAgentEnabled(),
        routingPlan.workerPlans(),
        Map.of("routingSummary", routingPlan.summary(), "parentTaskType", request.type().name())
    ).taskId();
    storeContext(taskId, "harness-parent-task", request.title(), parentTaskPayload(request, repo));
    storeContext(taskId, "harness-routing-plan", routingPlan.summary(), Map.of("workerPlans", routingPlan.workerPlans()));
    if (!request.codebaseInput().isEmpty() || !request.changedFiles().isEmpty()) {
      Map<String, Object> payload = new LinkedHashMap<>(request.codebaseInput());
      payload.put("changedFiles", request.changedFiles());
      payload.put("gitBase", request.gitBase());
      payload.put("gitHead", request.gitHead());
      storeContext(taskId, "harness-codebase-input", "Codebase and diff input.", payload);
    }
    if (!request.storedContextInput().isEmpty()) {
      storeContext(taskId, "harness-stored-context", "Stored task and run context.", request.storedContextInput());
    }
    if (!request.ruleInput().isEmpty()) {
      storeContext(taskId, "harness-rule-input", "Architecture and rule input.", request.ruleInput());
    }
    if (!request.liveDebugInput().isEmpty()) {
      storeContext(taskId, "harness-live-debug-input", "Live debug and computer-use input.", request.liveDebugInput());
    }
    return harnessStateService.loadState(taskId);
  }

  private void storeContext(String taskId, String contextKey, String summary, Map<String, Object> payload) {
    sharedTaskContextService.storeSharedTaskContext(taskId, null, contextKey, "team", summary, payload);
  }

  private Path resolveRepoPath(String repoRef) {
    if (repoRef == null || repoRef.isBlank() || "current-worktree".equals(repoRef.strip())) {
      return Path.of(".").toAbsolutePath().normalize();
    }
    return Path.of(repoRef).toAbsolutePath().normalize();
  }

  private Map<String, Object> parentTaskPayload(ParentTaskRequest request, KnownRepo repo) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("taskId", request.taskId());
    payload.put("type", request.type().name());
    payload.put("title", request.title());
    payload.put("description", request.description());
    payload.put("repoPath", repo.repoPath());
    payload.put("priority", request.priority());
    payload.put("requestedBy", request.requestedBy());
    payload.put("requiresCleanupReview", request.requiresCleanupReview());
    payload.put("requiresIntegrationTests", request.requiresIntegrationTests());
    payload.put("requestedWorkerTypes", request.requestedWorkerTypes());
    payload.put("metadata", request.metadata());
    return payload;
  }
}
