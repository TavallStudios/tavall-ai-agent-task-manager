package org.tavall.ai.app.memory;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tavall.ai.app.config.MemoryRuntimeProperties;
import org.tavall.ai.app.console.Log;
import org.tavall.ai.app.persistence.postgres.MemoryEventRepository;
import org.tavall.ai.app.persistence.redis.MemoryRuntimeHotStateStore;

@Service
public class MemoryRuntimeService {

  private final MemoryRuntimeProperties properties;
  private final MemoryContinuityService continuityService;
  private final MemoryEventRepository eventRepository;
  private final MemoryRetrievalService retrievalService;
  private final MemoryRuntimeHotStateStore hotStateStore;

  public MemoryRuntimeService(
      MemoryRuntimeProperties properties,
      MemoryContinuityService continuityService,
      MemoryEventRepository eventRepository,
      MemoryRetrievalService retrievalService,
      MemoryRuntimeHotStateStore hotStateStore
  ) {
    this.properties = properties;
    this.continuityService = continuityService;
    this.eventRepository = eventRepository;
    this.retrievalService = retrievalService;
    this.hotStateStore = hotStateStore;
  }

  public MemoryTurnHandle beginTurn(
      String requestId,
      String projectId,
      String threadKey,
      String sessionId,
      String requestedBy,
      String requestedFrom,
      String repoPath,
      String requestText,
      String queryText,
      Map<String, Object> metadata
  ) {
    long start = System.nanoTime();
    MemoryIdentity identity = retrievalService.resolveIdentity(
        projectId,
        threadKey,
        sessionId,
        requestedBy,
        requestedFrom,
        repoPath,
        metadata
    );
    String ingressEventId = eventRepository.recordEvent(
        requestId,
        "ingress",
        "user-message",
        requestId + ":ingress",
        identity,
        summarize(requestText),
        payload(metadata, "requestText", requestText)
    );
    MemoryHydration hydration = retrievalService.lookup(
        projectId,
        identity.threadKey(),
        sessionId,
        identity.requestedBy(),
        identity.requestedFrom(),
        repoPath,
        queryText,
        metadata
    );
    String lookupEventId = eventRepository.recordEvent(
        requestId,
        "lookup",
        "memory-lookup",
        requestId + ":lookup",
        identity,
        hydration.summary(),
        Map.of(
            "section", hydration.section(),
            "exactCount", hydration.exactRecords().size(),
            "semanticCount", hydration.semanticCandidates().size(),
            "durationMs", elapsedMs(start)
        )
    );
    hotStateStore.incrementCounter("pipeline-begin");
    return new MemoryTurnHandle(requestId, ingressEventId, lookupEventId, blank(requestText), identity, hydration);
  }

  @Transactional
  public Map<String, Object> completeTurn(MemoryTurnHandle handle, String responseText, boolean failed) {
    if (!hotStateStore.claimIdempotency(handle.requestId() + ":complete", properties.getIdempotencyTtl())) {
      hotStateStore.incrementCounter("pipeline-duplicate");
      return Map.of(
          "actions", List.of(),
          "memoryIds", List.of(),
          "duplicate", true
      );
    }
    boolean locked = hotStateStore.acquireLock(handle.identity().cacheKey(), properties.getIdempotencyTtl());
    long start = System.nanoTime();
    try {
      MemoryContinuitySnapshot snapshot = continuityService.refresh(handle);
      Map<String, Object> result = Map.of(
          "actions", List.of(),
          "memoryIds", List.of(),
          "continuitySnapshotId", snapshot.continuitySnapshotId(),
          "durationMs", elapsedMs(start),
          "durableMemoryMutation", false
      );
      eventRepository.recordEvent(
          handle.requestId(),
          "complete",
          "memory-continuity",
          handle.requestId() + ":complete-event",
          handle.identity(),
          failed ? "Turn failed; continuity refreshed without automatic memory mutation." : "Turn completed; continuity refreshed without automatic memory mutation.",
          result
      );
      hotStateStore.incrementCounter("pipeline-complete");
      return result;
    } catch (RuntimeException exception) {
      Log.warn("Memory runtime completion failed for requestId={}: {}", handle.requestId(), exception.getMessage());
      Log.exception(exception);
      eventRepository.recordEvent(
          handle.requestId(),
          "complete",
          "memory-failure",
          handle.requestId() + ":complete-failure",
          handle.identity(),
          exception.getMessage(),
          Map.of("failureType", exception.getClass().getName())
      );
      hotStateStore.incrementCounter("pipeline-failure");
      return Map.of("error", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
    } finally {
      if (locked) {
        hotStateStore.releaseLock(handle.identity().cacheKey());
      }
    }
  }

  private Map<String, Object> payload(Map<String, Object> metadata, String key, String value) {
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>(metadata == null ? Map.of() : metadata);
    payload.put(key, blank(value));
    return payload;
  }

  private long elapsedMs(long start) {
    return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
  }

  private String summarize(String value) {
    String normalized = blank(value).replaceAll("\\s+", " ");
    if (normalized.length() <= 220) {
      return normalized;
    }
    return normalized.substring(0, 217) + "...";
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}
