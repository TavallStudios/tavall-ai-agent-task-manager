package com.agenttaskmanager.app.persistence.redis;

import com.agenttaskmanager.app.console.Log;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemoryRuntimeHotStateStore {

  private static final String ROOT = "agent-task-manager:memory-runtime";

  private final ObjectMapper objectMapper;
  private final StringRedisTemplate redisTemplate;
  private final AtomicBoolean localFallbackEnabled = new AtomicBoolean();
  private final Map<String, String> fallbackValues = new ConcurrentHashMap<>();

  public MemoryRuntimeHotStateStore(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
    this.objectMapper = objectMapper;
    this.redisTemplate = redisTemplate;
  }

  public boolean claimIdempotency(String key, Duration ttl) {
    String fullKey = ROOT + ":idempotency:" + key;
    return setIfAbsent(fullKey, ttl);
  }

  public boolean acquireLock(String key, Duration ttl) {
    String fullKey = ROOT + ":lock:" + key;
    return setIfAbsent(fullKey, ttl);
  }

  public void releaseLock(String key) {
    String fullKey = ROOT + ":lock:" + key;
    if (shouldUseFallback()) {
      fallbackValues.remove(fullKey);
      return;
    }
    try {
      redisTemplate.delete(fullKey);
    } catch (RuntimeException exception) {
      activateFallback(exception);
      fallbackValues.remove(fullKey);
    }
  }

  public Optional<String> loadWorkingMemory(String key) {
    return load(ROOT + ":working:" + key);
  }

  public void storeWorkingMemory(String key, Object value, Duration ttl) {
    store(ROOT + ":working:" + key, value, ttl);
  }

  public Optional<String> loadContinuitySnapshot(String key) {
    return load(ROOT + ":continuity:" + key);
  }

  public void storeContinuitySnapshot(String key, Object value, Duration ttl) {
    store(ROOT + ":continuity:" + key, value, ttl);
  }

  public void incrementCounter(String counterName) {
    String fullKey = ROOT + ":counter:" + counterName;
    if (shouldUseFallback()) {
      fallbackValues.merge(fullKey, "1", (current, ignored) -> Integer.toString(Integer.parseInt(current) + 1));
      return;
    }
    try {
      redisTemplate.opsForValue().increment(fullKey);
    } catch (RuntimeException exception) {
      activateFallback(exception);
      fallbackValues.merge(fullKey, "1", (current, ignored) -> Integer.toString(Integer.parseInt(current) + 1));
    }
  }

  public <T> Optional<T> readJson(String value, TypeReference<T> typeReference) {
    try {
      return Optional.ofNullable(objectMapper.readValue(value, typeReference));
    } catch (Exception exception) {
      return Optional.empty();
    }
  }

  private boolean setIfAbsent(String key, Duration ttl) {
    if (shouldUseFallback()) {
      return fallbackValues.putIfAbsent(key, "1") == null;
    }
    try {
      Boolean claimed = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
      return Boolean.TRUE.equals(claimed);
    } catch (RuntimeException exception) {
      activateFallback(exception);
      return fallbackValues.putIfAbsent(key, "1") == null;
    }
  }

  private Optional<String> load(String key) {
    if (shouldUseFallback()) {
      return Optional.ofNullable(fallbackValues.get(key));
    }
    try {
      return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    } catch (RuntimeException exception) {
      activateFallback(exception);
      return Optional.ofNullable(fallbackValues.get(key));
    }
  }

  private void store(String key, Object value, Duration ttl) {
    String serialized = writeJson(value);
    if (shouldUseFallback()) {
      fallbackValues.put(key, serialized);
      return;
    }
    try {
      redisTemplate.opsForValue().set(key, serialized, ttl);
    } catch (RuntimeException exception) {
      activateFallback(exception);
      fallbackValues.put(key, serialized);
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize memory hot-state payload.", exception);
    }
  }

  private boolean shouldUseFallback() {
    return localFallbackEnabled.get();
  }

  private void activateFallback(RuntimeException exception) {
    if (localFallbackEnabled.compareAndSet(false, true)) {
      Log.warn("Memory hot-state Redis unavailable. Falling back to in-memory cache: {}", exception.getMessage());
    }
  }
}
