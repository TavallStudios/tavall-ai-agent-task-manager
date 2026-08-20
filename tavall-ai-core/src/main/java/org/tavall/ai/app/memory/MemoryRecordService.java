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
import org.tavall.ai.app.persistence.postgres.MemoryRecordRepository;
import org.tavall.ai.app.retrieval.SemanticCollectionDomain;
import org.tavall.ai.app.retrieval.SemanticContentType;
import org.tavall.ai.app.retrieval.SemanticDocumentRequest;
import org.tavall.ai.app.retrieval.SemanticMemoryService;

@Service
public class MemoryRecordService {

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
    MemoryMutationPlan plan = plan(request);
    String sourceReference = blank(request.sourceReference());
    String sourceEventId = sourceReference.isBlank() ? "explicit-memory:" + plan.titleKey() : sourceReference;

    MemoryRecord record;
    if (!blank(request.supersedesMemoryId()).isBlank()) {
      record = recordRepository.createMemory(identity, "", sourceEventId, plan);
      MemoryRecord superseded = recordRepository.getById(request.supersedesMemoryId().strip());
      recordRepository.supersede(superseded.memoryId(), record.memoryId());
      deleteSemanticRecord(identity, superseded);
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

    syncSemanticRecord(identity, record, sourceReference);
    retrievalService.refreshExactState(identity);
    return record;
  }

  private MemoryMutationPlan plan(MemoryWriteRequest request) {
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
        blank(request.consentLevel()).isBlank() ? "explicit" : request.consentLevel().strip(),
        Map.copyOf(metadata)
    );
  }

  private void syncSemanticRecord(MemoryIdentity identity, MemoryRecord record, String sourceReference) {
    if (identity.projectId().isBlank()) {
      return;
    }
    Map<String, Object> payload = new LinkedHashMap<>(record.metadata());
    payload.put("memoryId", record.memoryId());
    payload.put("userId", identity.userId());
    payload.put("workspaceId", identity.workspaceId());
    payload.put("projectId", identity.projectId());
    payload.put("threadKey", identity.threadKey());
    payload.put("scope", record.scope().name());
    payload.put("status", record.status());
    payload.put("tombstoned", record.tombstoned());
    payload.put("importance", record.importance());
    payload.put("updatedAt", record.updatedAt().toString());
    payload.put("writeMode", "explicit");
    if (!sourceReference.isBlank()) {
      payload.put("sourceReference", sourceReference);
    }
    semanticMemoryService.storeProjectDocument(
        identity.projectId(),
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

  private void deleteSemanticRecord(MemoryIdentity identity, MemoryRecord record) {
    if (!identity.projectId().isBlank()) {
      semanticMemoryService.deleteProjectContexts(identity.projectId(), Map.of("memoryId", record.memoryId()));
    }
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

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}
