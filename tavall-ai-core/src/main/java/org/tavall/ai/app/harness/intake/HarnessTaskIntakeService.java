package org.tavall.ai.app.harness.intake;

import org.tavall.ai.app.harness.routing.HarnessRoutingPlan;
import org.tavall.ai.app.harness.routing.HarnessRoutingService;
import org.tavall.ai.app.harness.state.HarnessStateService;
import org.tavall.ai.app.harness.state.HarnessStateSnapshot;
import org.tavall.ai.app.model.KnownRepo;
import org.tavall.ai.app.orchestration.OverseerOrchestrationService;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import org.tavall.ai.app.retrieval.SemanticCollectionDomain;
import org.tavall.ai.app.retrieval.SemanticContentType;
import org.tavall.ai.app.service.RepoCatalogService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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
        false,
        routingPlan.workerPlans(),
        Map.of(
            "routingSummary", routingPlan.summary(),
            "parentTaskType", request.type().name(),
            "multiAgentEnabledRequested", request.multiAgentEnabled(),
            "multiAgentEnabledDeprecated", true
        )
    ).taskId();
    storeContext(
        repo.projectKey(),
        taskId,
        "harness-parent-task",
        request.title(),
        parentTaskPayload(request, repo),
        SemanticCollectionDomain.TASK_HISTORY,
        SemanticContentType.RUN_SUMMARY
    );
    storeContext(
        repo.projectKey(),
        taskId,
        "harness-routing-plan",
        routingPlan.summary(),
        Map.of("workerPlans", routingPlan.workerPlans()),
        SemanticCollectionDomain.TASK_HISTORY,
        SemanticContentType.RUN_SUMMARY
    );
    if (!request.codebaseInput().isEmpty() || !request.changedFiles().isEmpty()) {
      Map<String, Object> payload = new LinkedHashMap<>(request.codebaseInput());
      payload.put("changedFiles", request.changedFiles());
      payload.put("gitBase", request.gitBase());
      payload.put("gitHead", request.gitHead());
      storeContext(
          repo.projectKey(),
          taskId,
          "harness-codebase-input",
          "Codebase and diff input.",
          payload,
          SemanticCollectionDomain.CODE_REPO,
          SemanticContentType.DIFF
      );
    }
    if (!request.storedContextInput().isEmpty()) {
      storeContext(
          repo.projectKey(),
          taskId,
          "harness-stored-context",
          "Stored task and run context.",
          request.storedContextInput(),
          SemanticCollectionDomain.TASK_HISTORY,
          SemanticContentType.RUN_SUMMARY
      );
    }
    if (!request.ruleInput().isEmpty()) {
      storeContext(
          repo.projectKey(),
          taskId,
          "harness-rule-input",
          "Architecture and rule input.",
          request.ruleInput(),
          SemanticCollectionDomain.KNOWLEDGE_RULES,
          SemanticContentType.DOCUMENTATION
      );
    }
    if (!request.liveDebugInput().isEmpty()) {
      storeContext(
          repo.projectKey(),
          taskId,
          "harness-live-debug-input",
          "Live debug and computer-use input.",
          request.liveDebugInput(),
          SemanticCollectionDomain.CHAT_ARTIFACT,
          SemanticContentType.CHAT
      );
    }
    return harnessStateService.loadState(taskId);
  }

  private void storeContext(
      String projectKey,
      String taskId,
      String contextKey,
      String summary,
      Map<String, Object> payload,
      SemanticCollectionDomain domain,
      SemanticContentType contentType
  ) {
    sharedTaskContextService.storeSharedTaskContext(taskId, null, contextKey, "team", summary, payload);
    sharedTaskContextService.storeProjectSemanticDocument(
        projectKey,
        taskId,
        null,
        contextKey,
        summary,
        renderSemanticBody(summary, payload),
        domain,
        contentType,
        payload
    );
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

  private String renderSemanticBody(String summary, Map<String, Object> payload) {
    List<String> lines = new java.util.ArrayList<>();
    if (summary != null && !summary.isBlank()) {
      lines.add(summary.strip());
    }
    if (payload != null) {
      payload.forEach((key, value) -> lines.add(key + ": " + String.valueOf(value)));
    }
    return String.join("\n", lines).strip();
  }
}

