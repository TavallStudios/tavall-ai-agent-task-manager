package com.agenttaskmanager.app.retrieval;

import cache.SemanticContextCache;
import com.agenttaskmanager.app.config.MemorySyncProperties;
import com.agenttaskmanager.app.persistence.postgres.SemanticSyncOutboxEntry;
import com.agenttaskmanager.app.persistence.postgres.SemanticSyncOutboxRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SemanticSyncService {

  private final MemorySyncProperties properties;
  private final QdrantHealthService qdrantHealthService;
  private final SemanticContextCache semanticContextCache;
  private final SemanticSyncOutboxRepository outboxRepository;
  private final SemanticVectorStoreService semanticVectorStoreService;

  public SemanticSyncService(
      MemorySyncProperties properties,
      QdrantHealthService qdrantHealthService,
      SemanticContextCache semanticContextCache,
      SemanticSyncOutboxRepository outboxRepository,
      SemanticVectorStoreService semanticVectorStoreService
  ) {
    this.properties = properties;
    this.qdrantHealthService = qdrantHealthService;
    this.semanticContextCache = semanticContextCache;
    this.outboxRepository = outboxRepository;
    this.semanticVectorStoreService = semanticVectorStoreService;
  }

  public List<String> storeProjectDocument(String projectKey, SemanticDocumentRequest request, String dedupeKey) {
    return storeProjectDocument(projectKey, request, dedupeKey, SemanticSyncMode.WRITE_THROUGH);
  }

  public List<String> storeKnowledgeDocument(String knowledgeBase, SemanticDocumentRequest request, String dedupeKey) {
    return storeKnowledgeDocument(knowledgeBase, request, dedupeKey, SemanticSyncMode.WRITE_THROUGH);
  }

  public List<String> storeProjectDocument(
      String projectKey,
      SemanticDocumentRequest request,
      String dedupeKey,
      SemanticSyncMode mode
  ) {
    SemanticSyncOutboxEntry entry = outboxRepository.enqueueProjectUpsert(projectKey, request, dedupeKey);
    return mode == SemanticSyncMode.BACKGROUND_ONLY ? List.of() : storeUpsert(entry);
  }

  public List<String> storeKnowledgeDocument(
      String knowledgeBase,
      SemanticDocumentRequest request,
      String dedupeKey,
      SemanticSyncMode mode
  ) {
    SemanticSyncOutboxEntry entry = outboxRepository.enqueueKnowledgeUpsert(knowledgeBase, request, dedupeKey);
    return mode == SemanticSyncMode.BACKGROUND_ONLY ? List.of() : storeUpsert(entry);
  }

  public void deleteProject(String projectKey, Map<String, Object> payloadFilter, String dedupeKey) {
    applyDelete(outboxRepository.enqueueProjectDelete(projectKey, payloadFilter, dedupeKey));
  }

  public void deleteKnowledge(String knowledgeBase, Map<String, Object> payloadFilter, String dedupeKey) {
    applyDelete(outboxRepository.enqueueKnowledgeDelete(knowledgeBase, payloadFilter, dedupeKey));
  }

  public int processPendingOperations() {
    if (!properties.isEnabled()) {
      return 0;
    }
    List<SemanticSyncOutboxEntry> entries = outboxRepository.claimBatch(properties.getOutboxBatchSize());
    for (SemanticSyncOutboxEntry entry : entries) {
      if (isUpsert(entry.operationKind())) {
        storeUpsert(entry);
      } else {
        applyDelete(entry);
      }
    }
    return entries.size();
  }

  public long pendingCount() {
    return outboxRepository.countPending();
  }

  private List<String> storeUpsert(SemanticSyncOutboxEntry entry) {
    try {
      List<String> pointIds = applyUpsert(entry);
      completeOrRetry(entry.outboxId());
      semanticContextCache.clear();
      return pointIds;
    } catch (RuntimeException exception) {
      queueRetry(entry.outboxId(), exception.getMessage());
      return List.of();
    }
  }

  private void applyDelete(SemanticSyncOutboxEntry entry) {
    try {
      if ("project-delete".equals(entry.operationKind())) {
        semanticVectorStoreService.deleteProject(entry.scopeKey(), entry.payloadFilter());
      } else if ("knowledge-delete".equals(entry.operationKind())) {
        semanticVectorStoreService.deleteKnowledge(entry.scopeKey(), entry.payloadFilter());
      }
      completeOrRetry(entry.outboxId());
      semanticContextCache.clear();
    } catch (RuntimeException exception) {
      queueRetry(entry.outboxId(), exception.getMessage());
    }
  }

  private List<String> applyUpsert(SemanticSyncOutboxEntry entry) {
    SemanticDocumentRequest request = new SemanticDocumentRequest(
        blank(entry.documentId()),
        blank(entry.taskId()),
        blank(entry.workerTaskId()),
        blank(entry.semanticKind()),
        blank(entry.title()),
        blank(entry.content()),
        SemanticCollectionDomain.valueOf(entry.domain()),
        SemanticContentType.valueOf(entry.contentType()),
        entry.payload()
    );
    if ("project-upsert".equals(entry.operationKind())) {
      return semanticVectorStoreService.storeProjectDocument(entry.scopeKey(), request);
    }
    if ("knowledge-upsert".equals(entry.operationKind())) {
      return semanticVectorStoreService.upsertKnowledgeDocument(entry.scopeKey(), request);
    }
    throw new IllegalArgumentException("Unsupported semantic sync operation: " + entry.operationKind());
  }

  private void completeOrRetry(String outboxId) {
    if (qdrantHealthService.isWriteThroughHealthy()) {
      outboxRepository.markCompleted(outboxId);
      return;
    }
    queueRetry(outboxId, qdrantHealthService.currentSnapshot().summary());
  }

  private void queueRetry(String outboxId, String errorMessage) {
    outboxRepository.markQueued(
        outboxId,
        errorMessage,
        OffsetDateTime.now().plusNanos(Math.max(1000L, properties.getRetryDelayMs()) * 1_000_000L)
    );
  }

  private boolean isUpsert(String operationKind) {
    return "project-upsert".equals(operationKind) || "knowledge-upsert".equals(operationKind);
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }
}
