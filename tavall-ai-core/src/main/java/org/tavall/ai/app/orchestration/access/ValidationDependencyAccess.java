package org.tavall.ai.app.orchestration.access;

import org.tavall.ai.app.loader.ServiceLoaders;
import org.tavall.ai.app.model.validation.ValidationReport;
import java.nio.file.Path;

public interface ValidationDependencyAccess {

  default ValidationReport runValidationPipeline(String taskId, String workerTaskId, Path repoPath) {
    return ServiceLoaders.validationPipelineService().runValidationPipeline(taskId, workerTaskId, repoPath);
  }
}

