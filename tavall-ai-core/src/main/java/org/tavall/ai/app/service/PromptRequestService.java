package org.tavall.ai.app.service;

import org.tavall.ai.app.model.PromptRequestDetail;
import org.tavall.ai.app.model.PromptRequestSummary;
import org.tavall.ai.app.persistence.postgres.PromptRequestRepository;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class PromptRequestService {

  private static final List<String> EXECUTION_MODES = List.of("read-only", "edit", "run-tests");

  private final PromptRequestRepository promptRequestRepository;
  private final PromptThreadMemoryService promptThreadMemoryService;

  public PromptRequestService(
      PromptRequestRepository promptRequestRepository,
      PromptThreadMemoryService promptThreadMemoryService
  ) {
    this.promptRequestRepository = promptRequestRepository;
    this.promptThreadMemoryService = promptThreadMemoryService;
  }

  public PromptRequestSummary create(
      String projectKey,
      String repoPath,
      String bridgeTarget,
      String executionMode,
      String promptText,
      String requestedBy,
      String requestedFrom
  ) {
    return create(projectKey, repoPath, bridgeTarget, null, executionMode, promptText, requestedBy, requestedFrom);
  }

  public PromptRequestSummary create(
      String projectKey,
      String repoPath,
      String bridgeTarget,
      String threadKey,
      String executionMode,
      String promptText,
      String requestedBy,
      String requestedFrom
  ) {
    PromptRequestSummary summary = promptRequestRepository.create(
        projectKey,
        repoPath,
        bridgeTarget,
        threadKey,
        normalizeExecutionMode(executionMode),
        promptText,
        requestedBy,
        requestedFrom
    );
    promptThreadMemoryService.lookup(summary.projectKey(), summary.threadKey(), promptText);
    return summary;
  }

  public PromptRequestSummary createWorkerExecutionRequest(
      String projectKey,
      String repoPath,
      String threadKey,
      String promptText,
      String requestedBy,
      String requestedFrom,
      String taskId,
      String workerTaskId
  ) {
    PromptRequestSummary summary = promptRequestRepository.createWorkerRequest(
        projectKey,
        repoPath,
        threadKey,
        promptText,
        requestedBy,
        requestedFrom,
        Map.of(
            "taskId", taskId == null ? "" : taskId,
            "workerTaskId", workerTaskId == null ? "" : workerTaskId
        )
    );
    promptThreadMemoryService.lookup(summary.projectKey(), summary.threadKey(), promptText);
    return summary;
  }

  public List<PromptRequestSummary> list(int limit, String status) {
    return promptRequestRepository.list(limit, status);
  }

  public PromptRequestDetail getDetail(String requestId) {
    return promptRequestRepository.getDetail(requestId);
  }

  public long queuedPromptCount() {
    return promptRequestRepository.queuedPromptCount();
  }

  public static String buildThreadKey(String repoPath, String bridgeTarget) {
    return PromptRequestRepository.buildThreadKey(repoPath, bridgeTarget);
  }

  private static String normalizeExecutionMode(String executionMode) {
    String normalized = executionMode == null ? "" : executionMode.strip().toLowerCase(Locale.ROOT);
    if (!EXECUTION_MODES.contains(normalized)) {
      throw new IllegalArgumentException("Unsupported execution mode: " + executionMode);
    }
    return normalized;
  }
}
