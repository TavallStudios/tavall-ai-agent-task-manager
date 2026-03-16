package com.agenttaskmanager.app.orchestration;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PromptMemoryCaptureService {

  private final SharedTaskContextService sharedTaskContextService;

  public PromptMemoryCaptureService(SharedTaskContextService sharedTaskContextService) {
    this.sharedTaskContextService = sharedTaskContextService;
  }

  public void captureProjectMemory(
      String projectKey,
      String taskId,
      String workerTaskId,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    if (projectKey == null || projectKey.isBlank() || body == null || body.isBlank()) {
      return;
    }
    Map<String, Object> normalizedPayload = new LinkedHashMap<>();
    if (payload != null) {
      normalizedPayload.putAll(payload);
    }
    normalizedPayload.putIfAbsent("projectKey", projectKey);
    sharedTaskContextService.storeTaskEmbedding(
        projectKey,
        taskId,
        workerTaskId,
        kind,
        body.strip(),
        normalizedPayload
    );
  }
}
