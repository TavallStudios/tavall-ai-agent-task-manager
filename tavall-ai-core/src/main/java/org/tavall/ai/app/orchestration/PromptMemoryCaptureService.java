package org.tavall.ai.app.orchestration;

import java.util.LinkedHashMap;
import java.util.Map;
import org.tavall.ai.app.memory.MemoryIdentity;
import org.tavall.ai.app.memory.MemoryIdentityResolver;
import org.tavall.ai.app.retrieval.SemanticContextClassifier;
import org.springframework.stereotype.Service;

@Service
public class PromptMemoryCaptureService {

  private final MemoryIdentityResolver memoryIdentityResolver;
  private final SemanticContextClassifier semanticContextClassifier;
  private final SharedTaskContextService sharedTaskContextService;

  public PromptMemoryCaptureService(
      MemoryIdentityResolver memoryIdentityResolver,
      SemanticContextClassifier semanticContextClassifier,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.memoryIdentityResolver = memoryIdentityResolver;
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
    MemoryIdentity identity = memoryIdentityResolver.resolve(
        projectKey,
        stringValue(normalizedPayload, "threadKey"),
        stringValue(normalizedPayload, "sessionId"),
        stringValue(normalizedPayload, "requestedBy"),
        stringValue(normalizedPayload, "requestedFrom"),
        stringValue(normalizedPayload, "repoPath"),
        normalizedPayload
    );
    normalizedPayload.putIfAbsent("userId", identity.userId());
    normalizedPayload.putIfAbsent("workspaceId", identity.workspaceId());
    normalizedPayload.putIfAbsent("status", "active");
    normalizedPayload.putIfAbsent("tombstoned", false);
    normalizedPayload.putIfAbsent("scope", "PROJECT");
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

  private String stringValue(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    return value == null ? "" : String.valueOf(value).strip();
  }
}
