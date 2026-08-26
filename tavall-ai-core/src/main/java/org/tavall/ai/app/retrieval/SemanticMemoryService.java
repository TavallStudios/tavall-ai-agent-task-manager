package org.tavall.ai.app.retrieval;

import cache.CacheDomain;
import cache.CacheSource;
import cache.CacheType;
import cache.SemanticContextCache;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.tavall.ai.app.console.Log;
import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;

/**
 * Owns application-facing semantic memory operations.
 *
 * <p>Callers depend on this facade rather than Qdrant-specific stores or task-context services. The
 * lower-level sync/vector services remain responsible for chunking, embedding, outbox behavior, and
 * Qdrant transport.</p>
 */
@Service
public class SemanticMemoryService {

  private final SemanticContextCache semanticContextCache;
  private final SemanticSyncService semanticSyncService;
  private final SemanticVectorStoreService semanticVectorStoreService;

  public SemanticMemoryService(
      SemanticContextCache semanticContextCache,
      SemanticSyncService semanticSyncService,
      SemanticVectorStoreService semanticVectorStoreService
  ) {
    this.semanticContextCache = semanticContextCache;
    this.semanticSyncService = semanticSyncService;
    this.semanticVectorStoreService = semanticVectorStoreService;
  }

  public List<String> storeProjectDocument(
      String projectKey,
      String taskId,
      String workerTaskId,
      String kind,
      String title,
      String content,
      SemanticCollectionDomain domain,
      SemanticContentType contentType,
      Map<String, Object> payload
  ) {
    return storeProjectDocument(
        projectKey,
        new SemanticDocumentRequest(null, taskId, workerTaskId, kind, title, content, domain, contentType, payload),
        null
    );
  }

  public List<String> storeProjectDocument(
      String projectKey,
      SemanticDocumentRequest request,
      String dedupeKey
  ) {
    try {
      List<String> pointIds = semanticSyncService.storeProjectDocument(projectKey, request, dedupeKey);
      semanticContextCache.clear();
      return pointIds;
    } catch (RuntimeException exception) {
      Log.warn("Semantic project upsert failed for projectKey={}: {}", projectKey, exception.getMessage());
      return List.of();
    }
  }

  public void enqueueProjectDocument(
      String projectKey,
      SemanticDocumentRequest request,
      String dedupeKey
  ) {
    try {
      enqueueProjectDocumentStrict(projectKey, request, dedupeKey);
    } catch (RuntimeException exception) {
      Log.warn("Semantic project enqueue failed for projectKey={}: {}", projectKey, exception.getMessage());
    }
  }

  /** Enqueues a project semantic mutation and propagates persistence failures to the owning transaction. */
  public void enqueueProjectDocumentStrict(
      String projectKey,
      SemanticDocumentRequest request,
      String dedupeKey
  ) {
    semanticSyncService.storeProjectDocument(projectKey, request, dedupeKey, SemanticSyncMode.BACKGROUND_ONLY);
  }

  public List<String> storeKnowledgeDocument(
      String knowledgeBase,
      String documentId,
      String kind,
      String title,
      String content,
      SemanticContentType contentType,
      Map<String, Object> payload
  ) {
    return storeKnowledgeDocument(
        knowledgeBase,
        new SemanticDocumentRequest(
            documentId,
            null,
            null,
            kind,
            title,
            content,
            SemanticCollectionDomain.KNOWLEDGE_RULES,
            contentType,
            payload
        ),
        null
    );
  }

  public List<String> storeKnowledgeDocument(
      String knowledgeBase,
      SemanticDocumentRequest request,
      String dedupeKey
  ) {
    try {
      List<String> pointIds = semanticSyncService.storeKnowledgeDocument(knowledgeBase, request, dedupeKey);
      semanticContextCache.clear();
      return pointIds;
    } catch (RuntimeException exception) {
      Log.warn("Semantic knowledge upsert failed for knowledgeBase={}: {}", knowledgeBase, exception.getMessage());
      return List.of();
    }
  }

  public void enqueueKnowledgeDocument(
      String knowledgeBase,
      SemanticDocumentRequest request,
      String dedupeKey
  ) {
    try {
      semanticSyncService.storeKnowledgeDocument(knowledgeBase, request, dedupeKey, SemanticSyncMode.BACKGROUND_ONLY);
    } catch (RuntimeException exception) {
      Log.warn("Semantic knowledge enqueue failed for knowledgeBase={}: {}", knowledgeBase, exception.getMessage());
    }
  }

  public void deleteProjectContexts(String projectKey, Map<String, Object> payloadFilter) {
    deleteProjectContexts(projectKey, payloadFilter, null);
  }

  public void deleteProjectContexts(String projectKey, Map<String, Object> payloadFilter, String dedupeKey) {
    try {
      semanticSyncService.deleteProject(projectKey, payloadFilter, dedupeKey);
      semanticContextCache.clear();
    } catch (RuntimeException exception) {
      Log.warn("Semantic project delete failed for projectKey={}: {}", projectKey, exception.getMessage());
    }
  }

  /** Enqueues a project semantic delete and propagates persistence failures to the owning transaction. */
  public void enqueueProjectDeleteStrict(String projectKey, Map<String, Object> payloadFilter, String dedupeKey) {
    semanticSyncService.deleteProject(projectKey, payloadFilter, dedupeKey, SemanticSyncMode.BACKGROUND_ONLY);
  }

  public void deleteKnowledgeContexts(String knowledgeBase, Map<String, Object> payloadFilter) {
    deleteKnowledgeContexts(knowledgeBase, payloadFilter, null);
  }

  public void deleteKnowledgeContexts(String knowledgeBase, Map<String, Object> payloadFilter, String dedupeKey) {
    try {
      semanticSyncService.deleteKnowledge(knowledgeBase, payloadFilter, dedupeKey);
      semanticContextCache.clear();
    } catch (RuntimeException exception) {
      Log.warn("Semantic knowledge delete failed for knowledgeBase={}: {}", knowledgeBase, exception.getMessage());
    }
  }

  public void deleteLegacyCollection() {
    try {
      semanticVectorStoreService.deleteLegacyCollection();
      semanticContextCache.clear();
    } catch (RuntimeException exception) {
      Log.warn("Legacy semantic collection delete failed: {}", exception.getMessage());
    }
  }

  public long pendingCount() {
    return semanticSyncService.pendingCount();
  }

  public List<RetrievedSemanticContext> searchProject(
      String projectKey,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    return searchCollection(
        "project:" + projectKey,
        queryText,
        limit,
        payloadFilter,
        () -> semanticVectorStoreService.searchProject(projectKey, queryText, limit, payloadFilter),
        true
    );
  }

  /** Keeps configured semantic-store failures visible to the canonical memory compiler. */
  public List<RetrievedSemanticContext> searchProjectStrict(
      String projectKey,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    return searchCollection(
        "project:" + projectKey,
        queryText,
        limit,
        payloadFilter,
        () -> semanticVectorStoreService.searchProject(projectKey, queryText, limit, payloadFilter),
        false
    );
  }

  public List<RetrievedSemanticContext> searchKnowledge(
      String knowledgeBase,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    return searchCollection(
        "knowledge:" + knowledgeBase,
        queryText,
        limit,
        payloadFilter,
        () -> semanticVectorStoreService.searchKnowledge(knowledgeBase, queryText, limit, payloadFilter),
        true
    );
  }

  /** Keeps configured semantic-store failures visible to strict knowledge callers. */
  public List<RetrievedSemanticContext> searchKnowledgeStrict(
      String knowledgeBase,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    return searchCollection(
        "knowledge:" + knowledgeBase,
        queryText,
        limit,
        payloadFilter,
        () -> semanticVectorStoreService.searchKnowledge(knowledgeBase, queryText, limit, payloadFilter),
        false
    );
  }

  private List<RetrievedSemanticContext> searchCollection(
      String collectionKey,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter,
      SemanticSearchLoader loader,
      boolean suppressFailure
  ) {
    Map<String, Object> normalizedFilter = payloadFilter == null ? Map.of() : new TreeMap<>(payloadFilter);
    try {
      List<Map<String, Object>> cached = semanticContextCache.getOrLoad(
          collectionKey + ":" + queryText + ":" + limit + ":" + normalizedFilter,
          CacheDomain.RETRIEVAL,
          CacheType.SEMANTIC_CONTEXT,
          CacheSource.QDRANT,
          () -> loader.load().stream()
              .map(context -> Map.<String, Object>of(
                  "id", context.id(),
                  "score", context.score(),
                  "payload", context.payload()
              ))
              .toList()
      );
      return cached.stream()
          .map(item -> new RetrievedSemanticContext(
              String.valueOf(item.get("id")),
              ((Number) item.getOrDefault("score", 0.0D)).doubleValue(),
              (Map<String, Object>) item.getOrDefault("payload", Map.of())
          ))
          .toList();
    } catch (RuntimeException exception) {
      Log.warn(
          "Semantic search failed for collectionKey={} query='{}': {}",
          collectionKey,
          queryText,
          exception.getMessage()
      );
      if (!suppressFailure) {
        throw exception;
      }
      return List.of();
    }
  }

  @FunctionalInterface
  private interface SemanticSearchLoader {
    List<RetrievedSemanticContext> load();
  }
}
