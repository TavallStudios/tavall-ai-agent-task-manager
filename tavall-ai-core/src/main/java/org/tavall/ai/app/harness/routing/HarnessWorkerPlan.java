package org.tavall.ai.app.harness.routing;

import org.tavall.ai.app.model.orchestration.WorkerType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record HarnessWorkerPlan(
    WorkerType workerType,
    String taskRole,
    String title,
    boolean requiresCleanupReview,
    boolean requiresValidation,
    boolean requiresIntegrationTests,
    boolean patchArtifactRequired
) {

  public HarnessWorkerPlan {
    Objects.requireNonNull(workerType, "workerType");
    taskRole = normalizeRole(taskRole, workerType);
    title = title == null || title.isBlank() ? workerType.name() + " worker" : title.strip();
  }

  public Map<String, Object> metadata(String sourceRepo) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("workerType", workerType.name());
    metadata.put("sourceRepo", sourceRepo);
    metadata.put("requiresCleanupReview", requiresCleanupReview);
    metadata.put("requiresValidation", requiresValidation);
    metadata.put("requiresIntegrationTests", requiresIntegrationTests);
    metadata.put("patchArtifactRequired", patchArtifactRequired);
    return metadata;
  }

  private static String normalizeRole(String taskRole, WorkerType workerType) {
    if (taskRole == null || taskRole.isBlank()) {
      return workerType.defaultTaskRole();
    }
    return taskRole.strip();
  }
}

