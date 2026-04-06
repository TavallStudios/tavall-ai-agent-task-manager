package org.tavall.ai.app.orchestration.access;

import org.tavall.ai.app.loader.ServiceLoaders;
import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.model.orchestration.WorkerCheckIn;
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

