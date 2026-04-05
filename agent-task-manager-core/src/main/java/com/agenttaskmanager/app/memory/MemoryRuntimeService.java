package com.agenttaskmanager.app.memory;

import com.agenttaskmanager.app.config.MemoryRuntimeProperties;
import com.agenttaskmanager.app.console.Log;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.persistence.postgres.MemoryEventRepository;
import com.agenttaskmanager.app.persistence.postgres.MemoryRecordRepository;
import com.agenttaskmanager.app.persistence.redis.MemoryRuntimeHotStateStore;
import com.agenttaskmanager.app.retrieval.SemanticCollectionDomain;
import com.agenttaskmanager.app.retrieval.SemanticContentType;
import com.agenttaskmanager.app.retrieval.SemanticDocumentRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryRuntimeService {

  private final MemoryRuntimeProperties properties;
  private final MemoryContinuityService continuityService;
  private final MemoryEventRepository eventRepository;
  private final MemoryPolicyService policyService;
  private final MemoryRecordRepository recordRepository;
  private final MemoryRetrievalService retrievalService;
  private final MemoryRuntimeHotStateStore hotStateStore;
  private final SharedTaskContextService sharedTaskContextService;

  public MemoryRuntimeService(
      MemoryRuntimeProperties properties,
      MemoryContinuityService continuityService,
      MemoryEventRepository eventRepository,
      MemoryPolicyService policyService,
      MemoryRecordRepository recordRepository,
      MemoryRetrievalService retrievalService,
      MemoryRuntimeHotStateStore hotStateStore,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.properties = properties;
    this.continuityService = continuityService;
    this.eventRepository = eventRepository;
    this.policyService = policyService;
    this.recordRepository = recordRepository;
    this.retrievalService = retrievalService;
    this.hotStateStore = hotStateStore;
    this.sharedTaskContextService = sharedTaskContextService;
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
      return eventRepository.latestMutationSummary(handle.requestId());
    }
    boolean locked = hotStateStore.acquireLock(handle.identity().cacheKey(), properties.getIdempotencyTtl());
    long start = System.nanoTime();
    try {
      List<MemoryMutationPlan> plans = policyService.evaluate(handle.identity(), handle.requestText(), responseText, failed);
      List<String> actions = new ArrayList<>();
      List<String> memoryIds = new ArrayList<>();
      for (MemoryMutationPlan plan : plans) {
        if (plan.action() == MemoryAction.NOOP) {
          actions.add(plan.action().name());
          eventRepository.recordMutation(
              handle.requestId(),
              handle.lookupEventId(),
              "",
              plan.action().name(),
              plan.summary(),
              handle.requestId() + ":noop",
              Map.of("title", plan.title())
          );
          continue;
        }
        Optional<MemoryRecord> existing = recordRepository.findStableRecord(handle.identity(), plan);
        MemoryRecord record = mutateRecord(handle, plan, existing);
        actions.add(plan.action().name());
        memoryIds.add(record.memoryId());
        syncSemanticRecord(handle.identity(), record, plan);
        eventRepository.recordMutation(
            handle.requestId(),
            handle.lookupEventId(),
            record.memoryId(),
            plan.action().name(),
            plan.summary(),
            handle.requestId() + ":" + plan.kind().name() + ":" + plan.titleKey(),
            Map.of(
                "kind", plan.kind().name(),
                "scope", plan.scope().name(),
                "title", plan.title(),
                "memoryId", record.memoryId(),
                "version", record.version()
            )
        );
      }
      MemoryContinuitySnapshot snapshot = continuityService.refresh(handle);
      Map<String, Object> result = Map.of(
          "actions", actions,
          "memoryIds", memoryIds,
          "continuitySnapshotId", snapshot.continuitySnapshotId(),
          "durationMs", elapsedMs(start)
      );
      eventRepository.recordEvent(
          handle.requestId(),
          "complete",
          "memory-mutation",
          handle.requestId() + ":complete-event",
          handle.identity(),
          summarize(responseText),
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
      return Map.of("error", exception.getMessage());
    } finally {
      if (locked) {
        hotStateStore.releaseLock(handle.identity().cacheKey());
      }
    }
  }

  private MemoryRecord mutateRecord(
      MemoryTurnHandle handle,
      MemoryMutationPlan plan,
      Optional<MemoryRecord> existing
  ) {
    return switch (plan.action()) {
      case CREATE_EPISODIC_MEMORY -> recordRepository.createMemory(
          handle.identity(),
          handle.requestId(),
          handle.ingressEventId(),
          plan
      );
      case UPSERT_SEMANTIC_MEMORY, UPDATE_EXISTING_MEMORY, CLOSE_TASK -> upsertStableRecord(handle, plan, existing);
      case SUPERSEDE_MEMORY -> supersedeRecord(handle, plan, existing);
      case DELETE_TOMBSTONE_MEMORY -> {
        existing.ifPresent(record -> {
          recordRepository.tombstone(record.memoryId());
          deleteSemanticRecord(handle.identity(), record);
        });
        yield existing.orElseGet(() -> recordRepository.createMemory(handle.identity(), handle.requestId(), handle.ingressEventId(), plan));
      }
      case NOOP -> throw new IllegalStateException("NOOP should be handled before mutation.");
    };
  }

  private MemoryRecord upsertStableRecord(
      MemoryTurnHandle handle,
      MemoryMutationPlan plan,
      Optional<MemoryRecord> existing
  ) {
    if (existing.isEmpty()) {
      return recordRepository.createMemory(handle.identity(), handle.requestId(), handle.ingressEventId(), plan);
    }
    List<String> sourceEventIds = new ArrayList<>(existing.get().sourceEventIds());
    sourceEventIds.add(handle.ingressEventId());
    return recordRepository.updateMemory(
        existing.get().memoryId(),
        handle.requestId(),
        dedupe(sourceEventIds),
        plan
    );
  }

  private MemoryRecord supersedeRecord(
      MemoryTurnHandle handle,
      MemoryMutationPlan plan,
      Optional<MemoryRecord> existing
  ) {
    MemoryRecord replacement = recordRepository.createMemory(handle.identity(), handle.requestId(), handle.ingressEventId(), plan);
    existing.ifPresent(record -> {
      recordRepository.supersede(record.memoryId(), replacement.memoryId());
      deleteSemanticRecord(handle.identity(), record);
    });
    return replacement;
  }

  private void syncSemanticRecord(MemoryIdentity identity, MemoryRecord record, MemoryMutationPlan plan) {
    if (identity.projectId().isBlank()) {
      return;
    }
    Map<String, Object> payload = new LinkedHashMap<>(record.metadata());
    payload.put("memoryId", record.memoryId());
    payload.put("userId", identity.userId());
    payload.put("workspaceId", identity.workspaceId());
    payload.put("projectId", identity.projectId());
    payload.put("chatId", identity.chatId());
    payload.put("threadKey", identity.threadKey());
    payload.put("scope", record.scope().name());
    payload.put("status", record.status());
    payload.put("tombstoned", record.tombstoned());
    payload.put("importance", record.importance());
    payload.put("updatedAt", record.updatedAt().toString());
    sharedTaskContextService.storeProjectSemanticDocument(
        identity.projectId(),
        new SemanticDocumentRequest(
            record.memoryId(),
            handleTaskId(record),
            null,
            "memory-" + plan.kind().name().toLowerCase(),
            record.title(),
            buildSemanticBody(record),
            domainFor(record.kind()),
            contentTypeFor(record.kind()),
            payload
        ),
        "memory:" + record.memoryId() + ":v" + record.version()
    );
  }

  private void deleteSemanticRecord(MemoryIdentity identity, MemoryRecord record) {
    if (identity.projectId().isBlank()) {
      return;
    }
    sharedTaskContextService.deleteProjectSemanticContexts(identity.projectId(), Map.of("memoryId", record.memoryId()));
  }

  private String handleTaskId(MemoryRecord record) {
    Object taskId = record.metadata().get("taskId");
    return taskId == null ? "" : String.valueOf(taskId);
  }

  private String buildSemanticBody(MemoryRecord record) {
    String facts = String.join("\n", record.facts());
    return (record.title() + "\n" + record.summary() + "\n" + facts).strip();
  }

  private SemanticCollectionDomain domainFor(MemoryKind kind) {
    return kind == MemoryKind.EPISODIC ? SemanticCollectionDomain.CHAT_ARTIFACT : SemanticCollectionDomain.TASK_HISTORY;
  }

  private SemanticContentType contentTypeFor(MemoryKind kind) {
    return kind == MemoryKind.EPISODIC ? SemanticContentType.CHAT : SemanticContentType.RUN_SUMMARY;
  }

  private Map<String, Object> payload(Map<String, Object> metadata, String key, String value) {
    Map<String, Object> payload = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
    payload.put(key, blank(value));
    return payload;
  }

  private List<String> dedupe(List<String> sourceEventIds) {
    return List.copyOf(new LinkedHashSet<>(sourceEventIds));
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
