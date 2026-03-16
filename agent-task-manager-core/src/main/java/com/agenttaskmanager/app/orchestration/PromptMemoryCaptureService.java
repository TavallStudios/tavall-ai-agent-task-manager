package com.agenttaskmanager.app.orchestration;

import java.util.LinkedHashMap;
import java.util.Map;
import com.agenttaskmanager.app.retrieval.SemanticContextClassifier;
import org.springframework.stereotype.Service;

@Service
public class PromptMemoryCaptureService {

  private final SemanticContextClassifier semanticContextClassifier;
  private final SharedTaskContextService sharedTaskContextService;

  public PromptMemoryCaptureService(
      SemanticContextClassifier semanticContextClassifier,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.semanticContextClassifier = semanticContextClassifier;
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
    var classification = semanticContextClassifier.classify(kind, body, normalizedPayload);
    sharedTaskContextService.storeProjectSemanticDocument(
        projectKey,
        taskId,
        workerTaskId,
        kind,
        kind,
        body.strip(),
        classification.domain(),
        classification.contentType(),
        normalizedPayload
    );
  }
}
