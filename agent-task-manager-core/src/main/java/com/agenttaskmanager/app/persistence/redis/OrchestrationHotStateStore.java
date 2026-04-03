package com.agenttaskmanager.app.persistence.redis;

import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrchestrationHotStateStore {

  private static final String ROOT = "agent-task-manager:orchestration";
  private static final Logger LOGGER = LoggerFactory.getLogger(OrchestrationHotStateStore.class);

  private final InMemoryHotStateFallbackStore inMemoryHotStateFallbackStore;
  private final StringRedisTemplate redisTemplate;
  private final AtomicBoolean localFallbackEnabled = new AtomicBoolean();

  public OrchestrationHotStateStore(
      InMemoryHotStateFallbackStore inMemoryHotStateFallbackStore,
      StringRedisTemplate redisTemplate
  ) {
    this.inMemoryHotStateFallbackStore = inMemoryHotStateFallbackStore;
    this.redisTemplate = redisTemplate;
  }

  public void queueWorkerTask(String taskId, String workerTaskId) {
    if (shouldUseLocalFallback()) {
      inMemoryHotStateFallbackStore.queueWorkerTask(taskId, workerTaskId);
      return;
    }
    try {
      redisTemplate.opsForList().rightPush(workerQueueKey(taskId), workerTaskId);
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      inMemoryHotStateFallbackStore.queueWorkerTask(taskId, workerTaskId);
    }
  }

  public String claimQueuedWorkerTask(String taskId) {
    if (shouldUseLocalFallback()) {
      return inMemoryHotStateFallbackStore.claimQueuedWorkerTask(taskId);
    }
    try {
      return redisTemplate.opsForList().leftPop(workerQueueKey(taskId));
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      return inMemoryHotStateFallbackStore.claimQueuedWorkerTask(taskId);
    }
  }

  public long workerQueueDepth(String taskId) {
    if (shouldUseLocalFallback()) {
      return inMemoryHotStateFallbackStore.workerQueueDepth(taskId);
    }
    try {
      Long size = redisTemplate.opsForList().size(workerQueueKey(taskId));
      return size == null ? 0L : size;
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      return inMemoryHotStateFallbackStore.workerQueueDepth(taskId);
    }
  }

  public void recordWorkerHeartbeat(String workerTaskId, String agentId, Duration ttl) {
    if (shouldUseLocalFallback()) {
      inMemoryHotStateFallbackStore.recordWorkerHeartbeat(workerTaskId, agentId, ttl);
      return;
    }
    Map<String, String> values = new LinkedHashMap<>();
    values.put("agentId", agentId);
    values.put("heartbeatAt", Long.toString(System.currentTimeMillis()));
    String key = workerHeartbeatKey(workerTaskId);
    try {
      redisTemplate.opsForHash().putAll(key, values);
      redisTemplate.expire(key, ttl);
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      inMemoryHotStateFallbackStore.recordWorkerHeartbeat(workerTaskId, agentId, ttl);
    }
  }

  public void recordWorkerStatus(String workerTaskId, TaskLifecycleStatus status) {
    if (shouldUseLocalFallback()) {
      inMemoryHotStateFallbackStore.recordWorkerStatus(workerTaskId, status);
      return;
    }
    try {
      redisTemplate.opsForHash().put(workerHeartbeatKey(workerTaskId), "status", status.name());
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      inMemoryHotStateFallbackStore.recordWorkerStatus(workerTaskId, status);
    }
  }

  public Map<Object, Object> getWorkerStatus(String workerTaskId) {
    if (shouldUseLocalFallback()) {
      return inMemoryHotStateFallbackStore.getWorkerStatus(workerTaskId);
    }
    try {
      return redisTemplate.opsForHash().entries(workerHeartbeatKey(workerTaskId));
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      return inMemoryHotStateFallbackStore.getWorkerStatus(workerTaskId);
    }
  }

  public void acquireOverseerLock(String taskId, String agentId, Duration ttl) {
    if (shouldUseLocalFallback()) {
      inMemoryHotStateFallbackStore.acquireOverseerLock(taskId, agentId);
      return;
    }
    String key = overseerLockKey(taskId);
    try {
      redisTemplate.opsForValue().set(key, agentId, ttl);
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      inMemoryHotStateFallbackStore.acquireOverseerLock(taskId, agentId);
    }
  }

  public void acquireCleanupLock(String taskId, String agentId, Duration ttl) {
    if (shouldUseLocalFallback()) {
      inMemoryHotStateFallbackStore.acquireCleanupLock(taskId, agentId);
      return;
    }
    String key = cleanupLockKey(taskId);
    try {
      redisTemplate.opsForValue().set(key, agentId, ttl);
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      inMemoryHotStateFallbackStore.acquireCleanupLock(taskId, agentId);
    }
  }

  public void incrementCounter(String counterName) {
    if (shouldUseLocalFallback()) {
      inMemoryHotStateFallbackStore.incrementCounter(counterName);
      return;
    }
    try {
      redisTemplate.opsForValue().increment(counterKey(counterName));
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      inMemoryHotStateFallbackStore.incrementCounter(counterName);
    }
  }

  public long getCounter(String counterName) {
    if (shouldUseLocalFallback()) {
      return inMemoryHotStateFallbackStore.getCounter(counterName);
    }
    try {
      String value = redisTemplate.opsForValue().get(counterKey(counterName));
      if (value == null || value.isBlank()) {
        return 0;
      }
      return Long.parseLong(value);
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      return inMemoryHotStateFallbackStore.getCounter(counterName);
    }
  }

  public void recordComputerUseRunnerLease(String runnerId, String sessionId, Duration ttl) {
    if (shouldUseLocalFallback()) {
      inMemoryHotStateFallbackStore.recordComputerUseRunnerLease(runnerId, sessionId, ttl);
      return;
    }
    String key = computerUseRunnerLeaseKey(runnerId);
    Map<String, String> values = new LinkedHashMap<>();
    values.put("sessionId", sessionId == null ? "" : sessionId);
    values.put("heartbeatAt", Long.toString(System.currentTimeMillis()));
    try {
      redisTemplate.opsForHash().putAll(key, values);
      redisTemplate.expire(key, ttl);
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      inMemoryHotStateFallbackStore.recordComputerUseRunnerLease(runnerId, sessionId, ttl);
    }
  }

  public Map<Object, Object> getComputerUseRunnerLease(String runnerId) {
    if (shouldUseLocalFallback()) {
      return inMemoryHotStateFallbackStore.getComputerUseRunnerLease(runnerId);
    }
    try {
      return redisTemplate.opsForHash().entries(computerUseRunnerLeaseKey(runnerId));
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      return inMemoryHotStateFallbackStore.getComputerUseRunnerLease(runnerId);
    }
  }

  public void clearComputerUseRunnerLease(String runnerId) {
    if (shouldUseLocalFallback()) {
      inMemoryHotStateFallbackStore.clearComputerUseRunnerLease(runnerId);
      return;
    }
    try {
      redisTemplate.delete(computerUseRunnerLeaseKey(runnerId));
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      inMemoryHotStateFallbackStore.clearComputerUseRunnerLease(runnerId);
    }
  }

  public void recordHytaleSessionState(
      String learningSessionId,
      String automationPhase,
      boolean focusSafe,
      Map<String, String> metadata,
      Duration ttl
  ) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("automationPhase", automationPhase == null ? "" : automationPhase);
    values.put("focusSafe", Boolean.toString(focusSafe));
    values.put("updatedAt", Long.toString(System.currentTimeMillis()));
    if (metadata != null) {
      values.putAll(metadata);
    }
    if (shouldUseLocalFallback()) {
      inMemoryHotStateFallbackStore.recordHytaleSessionState(learningSessionId, values, ttl);
      return;
    }
    String key = hytaleSessionStateKey(learningSessionId);
    try {
      redisTemplate.opsForHash().putAll(key, values);
      redisTemplate.expire(key, ttl);
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      inMemoryHotStateFallbackStore.recordHytaleSessionState(learningSessionId, values, ttl);
    }
  }

  public Map<Object, Object> getHytaleSessionState(String learningSessionId) {
    if (shouldUseLocalFallback()) {
      return inMemoryHotStateFallbackStore.getHytaleSessionState(learningSessionId);
    }
    try {
      return redisTemplate.opsForHash().entries(hytaleSessionStateKey(learningSessionId));
    } catch (RuntimeException exception) {
      activateLocalFallback(exception);
      return inMemoryHotStateFallbackStore.getHytaleSessionState(learningSessionId);
    }
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

  private String computerUseRunnerLeaseKey(String runnerId) {
    return ROOT + ":computer-use:runners:" + runnerId;
  }

  private String hytaleSessionStateKey(String learningSessionId) {
    return ROOT + ":hytale:learning:" + learningSessionId;
  }

  private boolean shouldUseLocalFallback() {
    return localFallbackEnabled.get();
  }

  private void activateLocalFallback(RuntimeException exception) {
    if (localFallbackEnabled.compareAndSet(false, true)) {
      LOGGER.warn("Redis hot-state store unavailable. Falling back to in-memory storage: {}", exception.getMessage());
    }
  }
}
