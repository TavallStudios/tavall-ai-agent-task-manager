package com.agenttaskmanager.app.orchestration.access;

import com.agenttaskmanager.app.loader.ServiceLoaders;
import com.agenttaskmanager.app.model.orchestration.OverseerTaskBatch;
import com.agenttaskmanager.app.model.orchestration.TaskAssignment;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
import java.util.List;

public interface TaskPoolDependencyAccess {

  default OverseerTaskBatch createTaskBatch(
      String projectKey,
      String sourceRepo,
      String title,
      boolean multiAgentEnabled,
      List<String> workerRoles
  ) {
    return ServiceLoaders.taskPoolService().createTaskBatch(
        projectKey,
        sourceRepo,
        title,
        multiAgentEnabled,
        workerRoles
    );
  }

  default TaskAssignment assignWorkerTask(
      String workerTaskId,
      String agentId,
      WorkerTransportKind transportKind,
      String sessionId
  ) {
    return ServiceLoaders.taskPoolService().assignWorkerTask(
        workerTaskId,
        agentId,
        transportKind,
        sessionId
    );
  }
}
