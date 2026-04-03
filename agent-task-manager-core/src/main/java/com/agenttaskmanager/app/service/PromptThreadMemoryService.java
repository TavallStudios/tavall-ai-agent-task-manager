package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.model.PromptRequestSummary;
import com.agenttaskmanager.app.model.PromptThreadDetail;
import com.agenttaskmanager.app.model.PromptThreadMemoryLookupResult;
import com.agenttaskmanager.app.model.PromptThreadSummary;
import com.agenttaskmanager.app.orchestration.PromptMemoryCaptureService;
import com.agenttaskmanager.app.orchestration.PromptMemoryLookupService;
import com.agenttaskmanager.app.persistence.postgres.PromptThreadRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PromptThreadMemoryService {

  private static final String DEFAULT_EXECUTION_TARGET = "";

  private final PromptMemoryCaptureService promptMemoryCaptureService;
  private final PromptMemoryLookupService promptMemoryLookupService;
  private final PromptThreadRepository promptThreadRepository;

  public PromptThreadMemoryService(
      PromptMemoryCaptureService promptMemoryCaptureService,
      PromptMemoryLookupService promptMemoryLookupService,
      PromptThreadRepository promptThreadRepository
  ) {
    this.promptMemoryCaptureService = promptMemoryCaptureService;
    this.promptMemoryLookupService = promptMemoryLookupService;
    this.promptThreadRepository = promptThreadRepository;
  }

  public PromptThreadMemoryLookupResult lookup(String projectKey, String threadKey, String queryText) {
    return promptMemoryLookupService.lookup(projectKey, threadKey, queryText);
  }

  public void capturePromptThreadMessage(
      PromptRequestSummary request,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    if (request != null) {
      capturePromptThreadMessage(
          request.projectKey(),
          request.requestId(),
          request.threadKey(),
          request.repoPath(),
          request.bridgeTarget(),
          kind,
          body,
          payload
      );
    }
  }

  public void capturePromptThreadMessage(
      String projectKey,
      String requestId,
      String threadKey,
      String repoPath,
      String bridgeTarget,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    if (projectKey == null || projectKey.isBlank() || body == null || body.isBlank()) {
      return;
    }
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("requestId", requestId == null ? "" : requestId);
    metadata.put("threadKey", threadKey == null ? "" : threadKey);
    metadata.put("repoPath", repoPath == null ? "" : repoPath);
    metadata.put("bridgeTarget", bridgeTarget == null ? "" : bridgeTarget);
    if (payload != null) {
      metadata.putAll(payload);
    }
    promptMemoryCaptureService.captureProjectMemory(projectKey, requestId, null, kind, body, metadata);
  }

  public void capturePromptThreadSnapshot(String projectKey, String threadKey) {
    if (projectKey == null || projectKey.isBlank() || threadKey == null || threadKey.isBlank()) {
      return;
    }
    promptThreadRepository.findDetail(threadKey).ifPresent(detail -> promptMemoryCaptureService.captureProjectMemory(
        projectKey,
        detail.thread().lastRequestId(),
        null,
        "prompt-thread-snapshot",
        snapshotBody(detail),
        Map.of(
            "threadKey", detail.thread().threadKey(),
            "repoPath", detail.thread().repoPath(),
            "bridgeTarget", detail.thread().bridgeTarget(),
            "messageCount", detail.messages().size(),
            "requestCount", detail.requests().size()
        )
    ));
  }

  public List<PromptThreadSummary> searchThreads(String queryText, int limit) {
    return promptThreadRepository.search(queryText, limit, DEFAULT_EXECUTION_TARGET);
  }

  private String snapshotBody(PromptThreadDetail detail) {
    StringBuilder builder = new StringBuilder();
    builder.append("threadKey=").append(detail.thread().threadKey()).append("\n");
    builder.append("latestSummary=").append(blank(detail.thread().latestRequestSummary())).append("\n");
    detail.messages().stream()
        .skip(Math.max(0, detail.messages().size() - 20))
        .forEach(message -> builder.append("[")
            .append(message.messageKind())
            .append("] ")
            .append(blank(message.senderName()))
            .append(": ")
            .append(message.body() == null ? "" : message.body().strip())
            .append("\n"));
    return builder.toString().strip();
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }
}
