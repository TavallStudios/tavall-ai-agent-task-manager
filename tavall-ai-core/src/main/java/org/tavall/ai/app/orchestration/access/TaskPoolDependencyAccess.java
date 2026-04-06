package org.tavall.ai.app.orchestration.access;

import org.tavall.ai.app.loader.ServiceLoaders;
import org.tavall.ai.app.model.orchestration.OverseerTaskBatch;
import org.tavall.ai.app.model.orchestration.TaskAssignment;
import org.tavall.ai.app.model.orchestration.WorkerTransportKind;
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

