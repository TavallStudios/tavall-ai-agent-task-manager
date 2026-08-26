package org.tavall.ai.app.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.tavall.ai.app.console.Log;
import org.tavall.ai.app.persistence.postgres.MemoryRecordRepository;
import org.tavall.ai.app.retrieval.SemanticCollectionDomain;
import org.tavall.ai.app.retrieval.SemanticContentType;
import org.tavall.ai.app.retrieval.SemanticDocumentRequest;
import org.tavall.ai.app.retrieval.SemanticMemoryService;

@Service
public class MemoryRecordService {

  private static final String SEMANTIC_PROJECT_ID = "semanticProjectId";

  private final MemoryRecordRepository recordRepository;
  private final MemoryRetrievalService retrievalService;
  private final SemanticMemoryService semanticMemoryService;

  public MemoryRecordService(
      MemoryRecordRepository recordRepository,
      MemoryRetrievalService retrievalService,
      SemanticMemoryService semanticMemoryService
  ) {
    this.recordRepository = recordRepository;
    this.retrievalService = retrievalService;
    this.semanticMemoryService = semanticMemoryService;
  }

  @Transactional
  public MemoryRecord record(MemoryIdentity identity, MemoryWriteRequest request) {
    MemoryMutationPlan plan = plan(identity, request);
    String sourceReference = blank(request.sourceReference());
    String sourceEventId = sourceReference.isBlank() ? "explicit-memory:" + plan.titleKey() : sourceReference;
    String supersedesMemoryId = blank(request.supersedesMemoryId());

    MemoryRecord superseded = null;
    if (supersedesMemoryId.isBlank()) {
      recordRepository.lockStableRecordIdentity(identity, plan);
    } else {
      MemoryRecord candidate = accessibleSupersessionTarget(identity, plan.scope(), supersedesMemoryId);
      recordRepository.lockStableRecordIdentities(identity, plan, candidate);
      superseded = accessibleSupersessionTarget(identity, plan.scope(), supersedesMemoryId);
      Optional<MemoryRecord> conflictingReplacement = recordRepository.findStableRecord(identity, plan);
      if (conflictingReplacement.isPresent()
          && !conflictingReplacement.get().memoryId().equals(superseded.memoryId())) {
        throw new IllegalArgumentException(
            "an active memory already owns the replacement stable identity: "
                + conflictingReplacement.get().memoryId()
        );
      }
    }

    MemoryRecord record;
    if (superseded != null) {
      record = recordRepository.createMemory(identity, "", sourceEventId, plan);
      recordRepository.supersede(superseded.memoryId(), record.memoryId());
      enqueueSemanticDelete(superseded);
    } else {
      Optional<MemoryRecord> existing = recordRepository.findStableRecord(identity, plan);
      if (existing.isPresent()) {
        List<String> sources = new ArrayList<>(existing.get().sourceEventIds());
        sources.add(sourceEventId);
        record = recordRepository.updateMemory(
            existing.get().memoryId(),
            "",
            List.copyOf(new LinkedHashSet<>(sources)),
            plan
        );
      } else {
        record = recordRepository.createMemory(identity, "", sourceEventId, plan);
      }
    }

    enqueueSemanticRecord(record, sourceReference);
    refreshExactStateAfterCommit(identity, record.scope());
    return record;
  }

  private MemoryMutationPlan plan(MemoryIdentity identity, MemoryWriteRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("memory write request is required");
    }
    String title = required(request.title(), "title");
    String summary = required(request.summary(), "summary");
    MemoryScope scope = request.scope() == null ? MemoryScope.PROJECT : request.scope();
    MemoryKind kind = request.kind() == null ? MemoryKind.REFLECTION : request.kind();
    int importance = request.importance() == null ? 75 : Math.max(0, Math.min(100, request.importance()));
    Map<String, Object> metadata = new LinkedHashMap<>(request.metadata() == null ? Map.of() : request.metadata());
    metadata.put("writeMode", "explicit");
    if (!blank(identity.projectId()).isBlank()) {
      metadata.put(SEMANTIC_PROJECT_ID, identity.projectId().strip());
    }
    if (!blank(request.sourceReference()).isBlank()) {
      metadata.put("sourceReference", request.sourceReference().strip());
    }
    return new MemoryMutationPlan(
        MemoryAction.UPSERT_SEMANTIC_MEMORY,
        scope,
        kind,
        title,
        normalizeKey(title),
        summary,
        request.facts() == null ? List.of() : request.facts().stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::strip)
            .toList(),
        importance,
        blank(request.sensitivity()).isBlank() ? "internal" : request.sensitivity().strip(),
        "explicit",
        Map.copyOf(metadata)
    );
  }

  private MemoryRecord accessibleSupersessionTarget(
      MemoryIdentity identity,
      MemoryScope replacementScope,
      String memoryId
  ) {
    MemoryRecord record = recordRepository.getById(memoryId.strip());
    if (!same(record.userId(), identity.userId()) || !same(record.workspaceId(), identity.workspaceId())) {
      throw inaccessible(memoryId);
    }
    if (!"active".equals(record.status()) || record.tombstoned() || !blank(record.supersededBy()).isBlank()) {
      throw new IllegalArgumentException("superseded memory must be active: " + memoryId);
    }
    if (record.scope() != replacementScope) {
      throw new IllegalArgumentException(
          "superseding memory must preserve scope: existing=" + record.scope() + ", replacement=" + replacementScope
      );
    }
    boolean accessible = switch (record.scope()) {
      case GLOBAL -> true;
      case PROJECT -> same(record.projectId(), identity.projectId());
      case SESSION -> same(record.projectId(), identity.projectId())
          && same(record.chatId(), identity.chatId())
          && same(record.threadKey(), identity.threadKey());
    };
    if (!accessible) {
      throw inaccessible(memoryId);
    }
    return record;
  }

  private IllegalArgumentException inaccessible(String memoryId) {
    return new IllegalArgumentException("memory is outside the current authority scope: " + memoryId);
  }

  private void enqueueSemanticRecord(MemoryRecord record, String sourceReference) {
    String semanticProjectId = semanticProjectId(record);
    if (semanticProjectId.isBlank()) {
      return;
    }
    Map<String, Object> payload = new LinkedHashMap<>(record.metadata());
    payload.put("memoryId", record.memoryId());
    payload.put("userId", record.userId());
    payload.put("workspaceId", record.workspaceId());
    payload.put("projectId", record.projectId());
    payload.put("threadKey", record.threadKey());
    payload.put("scope", record.scope().name());
    payload.put("status", record.status());
    payload.put("tombstoned", record.tombstoned());
    payload.put("importance", record.importance());
    payload.put("updatedAt", record.updatedAt().toString());
    payload.put("writeMode", "explicit");
    payload.put(SEMANTIC_PROJECT_ID, semanticProjectId);
    if (!sourceReference.isBlank()) {
      payload.put("sourceReference", sourceReference);
    }
    semanticMemoryService.enqueueProjectDocumentStrict(
        semanticProjectId,
        new SemanticDocumentRequest(
            record.memoryId(),
            "",
            null,
            "memory-" + record.kind().name().toLowerCase(Locale.ROOT),
            record.title(),
            semanticBody(record),
            SemanticCollectionDomain.TASK_HISTORY,
            SemanticContentType.RUN_SUMMARY,
            payload
        ),
        "memory:" + record.memoryId() + ":v" + record.version()
    );
  }

  private void enqueueSemanticDelete(MemoryRecord record) {
    String semanticProjectId = semanticProjectId(record);
    if (!semanticProjectId.isBlank()) {
      semanticMemoryService.enqueueProjectDeleteStrict(
          semanticProjectId,
          Map.of("memoryId", record.memoryId()),
          "memory-delete:" + record.memoryId() + ":v" + record.version()
      );
    }
  }

  private void refreshExactStateAfterCommit(MemoryIdentity identity, MemoryScope scope) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      safeRefreshExactState(identity, scope);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        safeRefreshExactState(identity, scope);
      }
    });
  }

  private void safeRefreshExactState(MemoryIdentity identity, MemoryScope scope) {
    try {
      retrievalService.refreshExactStateAfterWrite(identity, scope);
    } catch (RuntimeException exception) {
      Log.warn(
          "Committed memory write could not refresh exact-state cache for projectId={} scope={}: {}",
          identity.projectId(),
          scope,
          exception.getMessage()
      );
    }
  }

  private String semanticProjectId(MemoryRecord record) {
    Object value = record.metadata().get(SEMANTIC_PROJECT_ID);
    String metadataProject = value == null ? "" : String.valueOf(value).strip();
    if (!metadataProject.isBlank()) {
      return metadataProject;
    }
    return blank(record.projectId());
  }

  private String semanticBody(MemoryRecord record) {
    String facts = String.join("\n", record.facts());
    return (record.title() + "\n" + record.summary() + "\n" + facts).strip();
  }

  private String normalizeKey(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }

  private String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required for explicit memory writes");
    }
    return value.strip();
  }

  private boolean same(String first, String second) {
    return blank(first).equals(blank(second));
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}
