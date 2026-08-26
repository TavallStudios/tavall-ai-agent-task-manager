package org.tavall.ai.app.orchestration;

import cache.CacheDomain;
import cache.CacheSource;
import cache.CacheType;
import cache.TaskContextCache;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.tavall.ai.app.model.orchestration.SharedTaskContext;
import org.tavall.ai.app.persistence.postgres.SharedTaskContextRepository;
import org.tavall.ai.app.persistence.postgres.WorkerTaskRepository;

@Service
public class SharedTaskContextService {

  private final SharedTaskContextRepository sharedTaskContextRepository;
  private final TaskContextCache taskContextCache;
  private final WorkerTaskRepository workerTaskRepository;

  public SharedTaskContextService(
      SharedTaskContextRepository sharedTaskContextRepository,
      TaskContextCache taskContextCache,
      WorkerTaskRepository workerTaskRepository
  ) {
    this.sharedTaskContextRepository = sharedTaskContextRepository;
    this.taskContextCache = taskContextCache;
    this.workerTaskRepository = workerTaskRepository;
  }

  public SharedTaskContext storeSharedTaskContext(
      String taskId,
      String workerTaskId,
      String contextKey,
      String visibility,
      String summary,
      Map<String, Object> payload
  ) {
    SharedTaskContext context = sharedTaskContextRepository.storeContext(
        taskId,
        workerTaskId,
        contextKey,
        visibility,
        summary,
        payload
    );
    invalidateTaskCache(taskId);
    return context;
  }

  public Map<String, Object> loadTaskContext(String taskId) {
    return taskContextCache.getOrLoad(
        taskId,
        CacheDomain.ORCHESTRATION,
        CacheType.TASK_CONTEXT,
        CacheSource.POSTGRES,
        () -> {
          Map<String, Object> payload = new LinkedHashMap<>();
          payload.put("taskId", taskId);
          payload.put("contexts", sharedTaskContextRepository.listByTask(taskId));
          payload.put("workerTasks", workerTaskRepository.listWorkerTasks(taskId));
          return payload;
        }
    );
  }

  public List<SharedTaskContext> listSharedTaskContext(String taskId) {
    return sharedTaskContextRepository.listByTask(taskId);
  }

  public List<Map<String, Object>> loadSiblingTaskSummaries(String taskId, String workerTaskId) {
    return workerTaskRepository.listWorkerTasks(taskId).stream()
        .filter(workerTask -> !workerTask.workerTaskId().equals(workerTaskId))
        .map(workerTask -> Map.<String, Object>of(
            "workerTaskId", workerTask.workerTaskId(),
            "taskRole", workerTask.taskRole(),
            "status", workerTask.status().name(),
            "summary", workerTask.latestSummary() == null ? "" : workerTask.latestSummary()
        ))
        .toList();
  }

  public void invalidateTaskCache(String taskId) {
    taskContextCache.invalidate(taskId, CacheDomain.ORCHESTRATION, CacheType.TASK_CONTEXT, CacheSource.POSTGRES);
  }
}
