package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.model.PromptRequestDetail;
import com.agenttaskmanager.app.model.PromptRequestSummary;
import com.agenttaskmanager.app.orchestration.PromptMemoryCaptureService;
import com.agenttaskmanager.app.persistence.postgres.PromptRequestRepository;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class PromptRequestService {

  private static final List<String> EXECUTION_MODES = List.of("read-only", "edit", "run-tests");

  private final PromptRequestRepository promptRequestRepository;
  private final PromptMemoryCaptureService promptMemoryCaptureService;

  public PromptRequestService(
      PromptRequestRepository promptRequestRepository,
      PromptMemoryCaptureService promptMemoryCaptureService
  ) {
    this.promptRequestRepository = promptRequestRepository;
    this.promptMemoryCaptureService = promptMemoryCaptureService;
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
    PromptRequestSummary summary = promptRequestRepository.create(
        projectKey,
        repoPath,
        bridgeTarget,
        normalizeExecutionMode(executionMode),
        promptText,
        requestedBy,
        requestedFrom
    );
    promptMemoryCaptureService.captureProjectMemory(
        projectKey,
        summary.requestId(),
        null,
        "prompt-request",
        promptText,
        Map.of(
            "requestId", summary.requestId(),
            "repoPath", repoPath,
            "bridgeTarget", bridgeTarget,
            "requestedBy", requestedBy,
            "requestedFrom", requestedFrom == null ? "" : requestedFrom
        )
    );
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
