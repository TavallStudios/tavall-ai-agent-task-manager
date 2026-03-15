package com.agenttaskmanager.app.orchestration.access;

import com.agenttaskmanager.app.loader.ServiceLoaders;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerCheckIn;
import java.util.Map;

public interface WorkerLifecycleDependencyAccess {

  default WorkerCheckIn submitWorkerCheckIn(
      String workerTaskId,
      String taskId,
      String agentId,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> details
  ) {
    return ServiceLoaders.workerLifecycleService().submitWorkerCheckIn(
        workerTaskId,
        taskId,
        agentId,
        status,
        summary,
        details
    );
  }
}
