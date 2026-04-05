package com.agenttaskmanager.app.memory;

import com.agenttaskmanager.app.config.MemoryRuntimeProperties;
import com.agenttaskmanager.app.persistence.postgres.MemoryContinuityRepository;
import com.agenttaskmanager.app.persistence.redis.MemoryRuntimeHotStateStore;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MemoryContinuityService {

  private final MemoryRuntimeProperties properties;
  private final MemoryContinuityRepository continuityRepository;
  private final MemoryRuntimeHotStateStore hotStateStore;
  private final MemoryRetrievalService retrievalService;

  public MemoryContinuityService(
      MemoryRuntimeProperties properties,
      MemoryContinuityRepository continuityRepository,
      MemoryRuntimeHotStateStore hotStateStore,
      MemoryRetrievalService retrievalService
  ) {
    this.properties = properties;
    this.continuityRepository = continuityRepository;
    this.hotStateStore = hotStateStore;
    this.retrievalService = retrievalService;
  }

  public MemoryContinuitySnapshot refresh(MemoryTurnHandle handle) {
    List<MemoryRecord> exactRecords = retrievalService.refreshExactState(handle.identity());
    Map<String, Object> sourceCounts = Map.of(
        "exact", exactRecords.size(),
        "semantic", handle.hydration().semanticCandidates().size()
    );
    MemoryContinuitySnapshot snapshot = continuityRepository.upsert(
        handle.identity(),
        handle.requestId(),
        handle.hydration().summary(),
        exactRecords.stream().map(this::toSnapshotItem).toList(),
        exactRecords.stream().map(MemoryRecord::memoryId).toList(),
        sourceCounts,
        Map.of("threadKey", handle.identity().threadKey(), "projectId", handle.identity().projectId())
    );
    hotStateStore.storeContinuitySnapshot(handle.identity().cacheKey(), snapshot, properties.getContinuityTtl());
    hotStateStore.incrementCounter("continuity-refresh");
    return snapshot;
  }

  public Optional<MemoryContinuitySnapshot> bootstrap(MemoryIdentity identity) {
    Optional<MemoryContinuitySnapshot> cached = hotStateStore.loadContinuitySnapshot(identity.cacheKey())
        .flatMap(serialized -> hotStateStore.readJson(serialized, new TypeReference<MemoryContinuitySnapshot>() {
        }));
    if (cached.isPresent()) {
      hotStateStore.incrementCounter("continuity-bootstrap-hit");
      return cached;
    }
    Optional<MemoryContinuitySnapshot> snapshot = continuityRepository.find(identity);
    snapshot.ifPresent(value -> hotStateStore.storeContinuitySnapshot(identity.cacheKey(), value, properties.getContinuityTtl()));
    hotStateStore.incrementCounter(snapshot.isPresent() ? "continuity-bootstrap-hit" : "continuity-bootstrap-miss");
    return snapshot;
  }

  private Map<String, Object> toSnapshotItem(MemoryRecord record) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("memoryId", record.memoryId());
    item.put("kind", record.kind().name());
    item.put("scope", record.scope().name());
    item.put("title", record.title());
    item.put("summary", record.summary());
    item.put("importance", record.importance());
    item.put("updatedAt", record.updatedAt());
    return item;
  }
}
