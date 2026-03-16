package com.agenttaskmanager.app.orchestration.access;

import com.agenttaskmanager.app.loader.ServiceLoaders;
import com.agenttaskmanager.app.model.validation.ValidationReport;
import java.nio.file.Path;

public interface ValidationDependencyAccess {

  default ValidationReport runValidationPipeline(String taskId, String workerTaskId, Path repoPath) {
    return ServiceLoaders.validationPipelineService().runValidationPipeline(taskId, workerTaskId, repoPath);
  }
}
