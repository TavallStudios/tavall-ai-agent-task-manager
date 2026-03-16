package com.agenttaskmanager.app.persistence.redis;

import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrchestrationHotStateStore {

  private static final String ROOT = "agent-task-manager:orchestration";

  private final StringRedisTemplate redisTemplate;

  public OrchestrationHotStateStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void queueWorkerTask(String taskId, String workerTaskId) {
    redisTemplate.opsForList().rightPush(workerQueueKey(taskId), workerTaskId);
  }

  public String claimQueuedWorkerTask(String taskId) {
    return redisTemplate.opsForList().leftPop(workerQueueKey(taskId));
  }

  public long workerQueueDepth(String taskId) {
    Long size = redisTemplate.opsForList().size(workerQueueKey(taskId));
    return size == null ? 0L : size;
  }

  public void recordWorkerHeartbeat(String workerTaskId, String agentId, Duration ttl) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("agentId", agentId);
    values.put("heartbeatAt", Long.toString(System.currentTimeMillis()));
    String key = workerHeartbeatKey(workerTaskId);
    redisTemplate.opsForHash().putAll(key, values);
    redisTemplate.expire(key, ttl);
  }

  public void recordWorkerStatus(String workerTaskId, TaskLifecycleStatus status) {
    redisTemplate.opsForHash().put(workerHeartbeatKey(workerTaskId), "status", status.name());
  }

  public Map<Object, Object> getWorkerStatus(String workerTaskId) {
    return redisTemplate.opsForHash().entries(workerHeartbeatKey(workerTaskId));
  }

  public void acquireOverseerLock(String taskId, String agentId, Duration ttl) {
    String key = overseerLockKey(taskId);
    redisTemplate.opsForValue().set(key, agentId, ttl);
  }

  public void acquireCleanupLock(String taskId, String agentId, Duration ttl) {
    String key = cleanupLockKey(taskId);
    redisTemplate.opsForValue().set(key, agentId, ttl);
  }

  public void incrementCounter(String counterName) {
    redisTemplate.opsForValue().increment(counterKey(counterName));
  }

  public long getCounter(String counterName) {
    String value = redisTemplate.opsForValue().get(counterKey(counterName));
    if (value == null || value.isBlank()) {
      return 0;
    }
    return Long.parseLong(value);
  }

  private String workerQueueKey(String taskId) {
    return ROOT + ":tasks:" + taskId + ":queue";
  }

  private String workerHeartbeatKey(String workerTaskId) {
    return ROOT + ":workers:" + workerTaskId;
  }

  private String overseerLockKey(String taskId) {
    return ROOT + ":locks:overseer:" + taskId;
  }

  private String cleanupLockKey(String taskId) {
    return ROOT + ":locks:cleanup:" + taskId;
  }

  private String counterKey(String counterName) {
    return ROOT + ":counters:" + counterName;
  }
}
