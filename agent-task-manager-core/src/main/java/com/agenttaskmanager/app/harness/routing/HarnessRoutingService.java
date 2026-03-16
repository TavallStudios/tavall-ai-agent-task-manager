package com.agenttaskmanager.app.harness.routing;

import com.agenttaskmanager.app.harness.intake.ParentTaskRequest;
import com.agenttaskmanager.app.harness.intake.ParentTaskType;
import com.agenttaskmanager.app.model.orchestration.WorkerType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HarnessRoutingService {

  public HarnessRoutingPlan routeTask(ParentTaskRequest request) {
    List<WorkerType> workerTypes = requestedWorkerTypes(request);
    List<HarnessWorkerPlan> workerPlans = workerTypes.stream()
        .map(workerType -> planFor(workerType, request))
        .toList();
    String summary = "Routed " + request.title() + " across " + workerPlans.size() + " specialized workers.";
    return new HarnessRoutingPlan(request, workerPlans, summary);
  }

  private List<WorkerType> requestedWorkerTypes(ParentTaskRequest request) {
    if (!request.requestedWorkerTypes().isEmpty()) {
      return request.requestedWorkerTypes().stream()
          .map(WorkerType::fromValue)
          .distinct()
          .toList();
    }
    LinkedHashSet<WorkerType> workerTypes = new LinkedHashSet<>(defaultWorkerTypes(request.type()));
    if (request.requiresCleanupReview()) {
      workerTypes.add(WorkerType.CLEANUP);
    }
    return List.copyOf(workerTypes);
  }

  private List<WorkerType> defaultWorkerTypes(ParentTaskType type) {
    List<WorkerType> workerTypes = new ArrayList<>();
    switch (type) {
      case FIX_OUTPUT, REFACTOR_FEATURE, GENERAL -> {
        workerTypes.add(WorkerType.CODE);
        workerTypes.add(WorkerType.RETRIEVAL);
      }
      case VALIDATE_PATCH -> {
        workerTypes.add(WorkerType.CODE);
        workerTypes.add(WorkerType.CLEANUP);
        workerTypes.add(WorkerType.RETRIEVAL);
      }
      case DEBUG_ISSUE, REPRODUCE_BUG -> {
        workerTypes.add(WorkerType.COMPUTER_USE);
        workerTypes.add(WorkerType.RETRIEVAL);
        workerTypes.add(WorkerType.CODE);
      }
      case CLEANUP_DIFFS -> {
        workerTypes.add(WorkerType.CLEANUP);
        workerTypes.add(WorkerType.RETRIEVAL);
      }
    }
    return workerTypes;
  }

  private HarnessWorkerPlan planFor(WorkerType workerType, ParentTaskRequest request) {
    boolean codeWorker = workerType == WorkerType.CODE;
    return new HarnessWorkerPlan(
        workerType,
        workerType.defaultTaskRole(),
        titleFor(workerType, request.title()),
        codeWorker && request.requiresCleanupReview(),
        codeWorker && workerType.validationRequired(),
        codeWorker && request.requiresIntegrationTests() && workerType.integrationTestsSupported(),
        codeWorker && workerType.patchArtifactRequired()
    );
  }

  private String titleFor(WorkerType workerType, String parentTitle) {
    return switch (workerType) {
      case CODE -> "Code worker for " + parentTitle;
      case CLEANUP -> "Cleanup worker for " + parentTitle;
      case COMPUTER_USE -> "Computer-use worker for " + parentTitle;
      case RETRIEVAL -> "Retrieval worker for " + parentTitle;
    };
  }
}
