package com.agenttaskmanager.app.persistence.redis;

import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class InMemoryHotStateFallbackStore {

  private final ConcurrentMap<String, ConcurrentLinkedDeque<String>> workerQueues = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Map<Object, Object>> workerStatus = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Long> workerStatusExpiry = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Map<Object, Object>> computerUseRunnerLease = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Long> computerUseRunnerLeaseExpiry = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Map<Object, Object>> hytaleSessionState = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Long> hytaleSessionStateExpiry = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> locks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

  public void queueWorkerTask(String taskId, String workerTaskId) {
    workerQueues.computeIfAbsent(taskId, ignored -> new ConcurrentLinkedDeque<>()).addLast(workerTaskId);
  }

  public String claimQueuedWorkerTask(String taskId) {
    ConcurrentLinkedDeque<String> queue = workerQueues.get(taskId);
    return queue == null ? null : queue.pollFirst();
  }

  public long workerQueueDepth(String taskId) {
    ConcurrentLinkedDeque<String> queue = workerQueues.get(taskId);
    return queue == null ? 0L : queue.size();
  }

  public void recordWorkerHeartbeat(String workerTaskId, String agentId, Duration ttl) {
    Map<Object, Object> values = new LinkedHashMap<>();
    values.put("agentId", agentId);
    values.put("heartbeatAt", Long.toString(System.currentTimeMillis()));
    workerStatus.compute(workerTaskId, (ignored, current) -> {
      Map<Object, Object> next = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
      next.putAll(values);
      return next;
    });
    workerStatusExpiry.put(workerTaskId, System.currentTimeMillis() + ttl.toMillis());
  }

  public void recordWorkerStatus(String workerTaskId, TaskLifecycleStatus status) {
    workerStatus.compute(workerTaskId, (ignored, current) -> {
      Map<Object, Object> next = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
      next.put("status", status.name());
      return next;
    });
  }

  public Map<Object, Object> getWorkerStatus(String workerTaskId) {
    Long expiresAt = workerStatusExpiry.get(workerTaskId);
    if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
      workerStatus.remove(workerTaskId);
      workerStatusExpiry.remove(workerTaskId);
      return Map.of();
    }
    Map<Object, Object> current = workerStatus.get(workerTaskId);
    return current == null ? Map.of() : Map.copyOf(current);
  }

  public void acquireOverseerLock(String taskId, String agentId) {
    locks.put("overseer:" + taskId, agentId);
  }

  public void acquireCleanupLock(String taskId, String agentId) {
    locks.put("cleanup:" + taskId, agentId);
  }

  public void incrementCounter(String counterName) {
    counters.computeIfAbsent(counterName, ignored -> new AtomicLong()).incrementAndGet();
  }

  public long getCounter(String counterName) {
    AtomicLong counter = counters.get(counterName);
    return counter == null ? 0L : counter.get();
  }

  public void recordComputerUseRunnerLease(String runnerId, String sessionId, Duration ttl) {
    Map<Object, Object> values = new LinkedHashMap<>();
    values.put("sessionId", sessionId == null ? "" : sessionId);
    values.put("heartbeatAt", Long.toString(System.currentTimeMillis()));
    computerUseRunnerLease.put(runnerId, values);
    computerUseRunnerLeaseExpiry.put(runnerId, System.currentTimeMillis() + ttl.toMillis());
  }

  public Map<Object, Object> getComputerUseRunnerLease(String runnerId) {
    Long expiresAt = computerUseRunnerLeaseExpiry.get(runnerId);
    if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
      computerUseRunnerLease.remove(runnerId);
      computerUseRunnerLeaseExpiry.remove(runnerId);
      return Map.of();
    }
    Map<Object, Object> current = computerUseRunnerLease.get(runnerId);
    return current == null ? Map.of() : Map.copyOf(current);
  }

  public void clearComputerUseRunnerLease(String runnerId) {
    computerUseRunnerLease.remove(runnerId);
    computerUseRunnerLeaseExpiry.remove(runnerId);
  }

  public void recordHytaleSessionState(String sessionId, Map<String, String> values, Duration ttl) {
    hytaleSessionState.put(sessionId, new LinkedHashMap<>(values));
    hytaleSessionStateExpiry.put(sessionId, System.currentTimeMillis() + ttl.toMillis());
  }

  public Map<Object, Object> getHytaleSessionState(String sessionId) {
    Long expiresAt = hytaleSessionStateExpiry.get(sessionId);
    if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
      hytaleSessionState.remove(sessionId);
      hytaleSessionStateExpiry.remove(sessionId);
      return Map.of();
    }
    Map<Object, Object> current = hytaleSessionState.get(sessionId);
    return current == null ? Map.of() : Map.copyOf(current);
  }
}
