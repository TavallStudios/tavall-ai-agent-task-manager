package cache;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public abstract class AbstractCache<T> {

  private final ConcurrentHashMap<CacheKeyEnvelope, CacheEntryEnvelope<T>> entries = new ConcurrentHashMap<>();
  private final AtomicInteger expiredEntries = new AtomicInteger();
  private final long defaultTtlMillis;

  protected AbstractCache(long defaultTtl, TimeUnit unit) {
    this.defaultTtlMillis = unit.toMillis(defaultTtl);
  }

  public T getOrLoad(
      String rawKey,
      CacheDomain domain,
      CacheType type,
      CacheSource source,
      Supplier<T> loader
  ) {
    CacheKeyEnvelope key = buildKey(rawKey, domain, type, source);
    long now = System.currentTimeMillis();
    CacheEntryEnvelope<T> existingEntry = entries.get(key);
    if (existingEntry != null && !existingEntry.isExpired(now)) {
      return existingEntry.value();
    }
    T loadedValue = loader.get();
    if (loadedValue != null) {
      put(rawKey, domain, type, source, loadedValue, defaultTtlMillis);
    }
    return loadedValue;
  }

  public void put(
      String rawKey,
      CacheDomain domain,
      CacheType type,
      CacheSource source,
      T value,
      long ttlMillis
  ) {
    if (value == null) {
      return;
    }
    CacheKeyEnvelope key = buildKey(rawKey, domain, type, source);
    entries.put(key, new CacheEntryEnvelope<>(value, System.currentTimeMillis() + ttlMillis));
  }

  public T getIfPresent(String rawKey, CacheDomain domain, CacheType type, CacheSource source) {
    CacheEntryEnvelope<T> entry = entries.get(buildKey(rawKey, domain, type, source));
    if (entry == null) {
      return null;
    }
    long now = System.currentTimeMillis();
    if (!entry.isExpired(now)) {
      return entry.value();
    }
    expiredEntries.incrementAndGet();
    entries.remove(buildKey(rawKey, domain, type, source));
    return null;
  }

  public void invalidate(String rawKey, CacheDomain domain, CacheType type, CacheSource source) {
    entries.remove(buildKey(rawKey, domain, type, source));
  }

  public void clear() {
    entries.clear();
  }

  public int cleanupExpired() {
    int removedEntries = 0;
    long now = System.currentTimeMillis();
    for (Iterator<Map.Entry<CacheKeyEnvelope, CacheEntryEnvelope<T>>> iterator = entries.entrySet().iterator();
         iterator.hasNext(); ) {
      Map.Entry<CacheKeyEnvelope, CacheEntryEnvelope<T>> entry = iterator.next();
      if (entry.getValue().isExpired(now)) {
        iterator.remove();
        expiredEntries.incrementAndGet();
        removedEntries++;
      }
    }
    return removedEntries;
  }

  public CacheStats getCacheStats() {
    return new CacheStats(entries.size(), expiredEntries.get());
  }

  protected CacheKeyEnvelope buildKey(
      String rawKey,
      CacheDomain domain,
      CacheType type,
      CacheSource source
  ) {
    return new CacheKeyEnvelope(rawKey, domain, type, CacheVersion.V1, source);
  }
}
