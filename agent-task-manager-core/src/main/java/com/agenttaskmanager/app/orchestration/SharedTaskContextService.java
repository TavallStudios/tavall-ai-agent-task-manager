package com.agenttaskmanager.app.orchestration;

import cache.CacheDomain;
import cache.CacheSource;
import cache.CacheType;
import cache.SemanticContextCache;
import cache.TaskContextCache;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.model.orchestration.SharedTaskContext;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import com.agenttaskmanager.app.persistence.postgres.SharedTaskContextRepository;
import com.agenttaskmanager.app.persistence.postgres.WorkerTaskRepository;
import com.agenttaskmanager.app.retrieval.SemanticCollectionDomain;
import com.agenttaskmanager.app.retrieval.SemanticContentType;
import com.agenttaskmanager.app.retrieval.SemanticDocumentRequest;
import com.agenttaskmanager.app.retrieval.SemanticSyncService;
import com.agenttaskmanager.app.retrieval.SemanticSyncMode;
import com.agenttaskmanager.app.retrieval.SemanticVectorStoreService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SharedTaskContextService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SharedTaskContextService.class);

  private final SemanticContextCache semanticContextCache;
  private final SemanticSyncService semanticSyncService;
  private final SemanticVectorStoreService semanticVectorStoreService;
  private final SharedTaskContextRepository sharedTaskContextRepository;
  private final TaskContextCache taskContextCache;
  private final WorkerTaskRepository workerTaskRepository;

  public SharedTaskContextService(
      SemanticContextCache semanticContextCache,
      SemanticSyncService semanticSyncService,
      SemanticVectorStoreService semanticVectorStoreService,
      SharedTaskContextRepository sharedTaskContextRepository,
      TaskContextCache taskContextCache,
      WorkerTaskRepository workerTaskRepository
  ) {
    this.semanticContextCache = semanticContextCache;
    this.semanticSyncService = semanticSyncService;
    this.semanticVectorStoreService = semanticVectorStoreService;
    this.sharedTaskContextRepository = sharedTaskContextRepository;
    this.taskContextCache = taskContextCache;
    this.workerTaskRepository = workerTaskRepository;
  }

  public SharedTaskContext storeSharedTaskContext(
      String taskId,
      String workerTaskId,
      String contextKey,
      String visibility,
      String summary,
      Map<String, Object> payload
  ) {
    SharedTaskContext context = sharedTaskContextRepository.storeContext(
        taskId,
        workerTaskId,
        contextKey,
        visibility,
        summary,
        payload
    );
    invalidateTaskCache(taskId);
    return context;
  }

  public Map<String, Object> loadTaskContext(String taskId) {
    return taskContextCache.getOrLoad(
        taskId,
        CacheDomain.ORCHESTRATION,
        CacheType.TASK_CONTEXT,
        CacheSource.POSTGRES,
        () -> {
          Map<String, Object> payload = new LinkedHashMap<>();
          payload.put("taskId", taskId);
          payload.put("contexts", sharedTaskContextRepository.listByTask(taskId));
          payload.put("workerTasks", workerTaskRepository.listWorkerTasks(taskId));
          return payload;
        }
    );
  }

  public List<SharedTaskContext> listSharedTaskContext(String taskId) {
    return sharedTaskContextRepository.listByTask(taskId);
  }

  public List<Map<String, Object>> loadSiblingTaskSummaries(String taskId, String workerTaskId) {
    return workerTaskRepository.listWorkerTasks(taskId).stream()
        .filter(workerTask -> !workerTask.workerTaskId().equals(workerTaskId))
        .map(workerTask -> Map.<String, Object>of(
            "workerTaskId", workerTask.workerTaskId(),
            "taskRole", workerTask.taskRole(),
            "status", workerTask.status().name(),
            "summary", workerTask.latestSummary() == null ? "" : workerTask.latestSummary()
        ))
        .toList();
  }

  public List<String> storeProjectSemanticDocument(
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
    try {
      return storeProjectSemanticDocument(
          projectKey,
          new SemanticDocumentRequest(null, taskId, workerTaskId, kind, title, content, domain, contentType, payload),
          null
      );
    } catch (RuntimeException exception) {
      LOGGER.warn("Semantic project upsert failed for projectKey={}: {}", projectKey, exception.getMessage());
      return List.of();
    }
  }

  public List<String> storeProjectSemanticDocument(
      String projectKey,
      SemanticDocumentRequest request,
      String dedupeKey
  ) {
    try {
      List<String> pointIds = semanticSyncService.storeProjectDocument(projectKey, request, dedupeKey);
      semanticContextCache.clear();
      return pointIds;
    } catch (RuntimeException exception) {
      LOGGER.warn("Semantic project upsert failed for projectKey={}: {}", projectKey, exception.getMessage());
      return List.of();
    }
  }

  public void enqueueProjectSemanticDocument(
      String projectKey,
      SemanticDocumentRequest request,
      String dedupeKey
  ) {
    try {
      semanticSyncService.storeProjectDocument(projectKey, request, dedupeKey, SemanticSyncMode.BACKGROUND_ONLY);
    } catch (RuntimeException exception) {
      LOGGER.warn("Semantic project enqueue failed for projectKey={}: {}", projectKey, exception.getMessage());
    }
  }

  public List<String> upsertKnowledgeDocument(
      String knowledgeBase,
      String documentId,
      String kind,
      String title,
      String content,
      SemanticContentType contentType,
      Map<String, Object> payload
  ) {
    try {
      return upsertKnowledgeDocument(
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
    } catch (RuntimeException exception) {
      LOGGER.warn("Semantic knowledge upsert failed for knowledgeBase={}: {}", knowledgeBase, exception.getMessage());
      return List.of();
    }
  }

  public List<String> upsertKnowledgeDocument(
      String knowledgeBase,
      SemanticDocumentRequest request,
      String dedupeKey
  ) {
    try {
      List<String> pointIds = semanticSyncService.storeKnowledgeDocument(knowledgeBase, request, dedupeKey);
      semanticContextCache.clear();
      return pointIds;
    } catch (RuntimeException exception) {
      LOGGER.warn("Semantic knowledge upsert failed for knowledgeBase={}: {}", knowledgeBase, exception.getMessage());
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
      LOGGER.warn("Semantic knowledge enqueue failed for knowledgeBase={}: {}", knowledgeBase, exception.getMessage());
    }
  }

  public void deleteProjectSemanticContexts(String projectKey, Map<String, Object> payloadFilter) {
    try {
      semanticSyncService.deleteProject(projectKey, payloadFilter, null);
      semanticContextCache.clear();
    } catch (RuntimeException exception) {
      LOGGER.warn("Semantic project delete failed for projectKey={}: {}", projectKey, exception.getMessage());
    }
  }

  public void deleteKnowledgeContexts(String knowledgeBase, Map<String, Object> payloadFilter) {
    try {
      semanticSyncService.deleteKnowledge(knowledgeBase, payloadFilter, null);
      semanticContextCache.clear();
    } catch (RuntimeException exception) {
      LOGGER.warn("Semantic knowledge delete failed for knowledgeBase={}: {}", knowledgeBase, exception.getMessage());
    }
  }

  public void deleteLegacySemanticCollection() {
    try {
      semanticVectorStoreService.deleteLegacyCollection();
      semanticContextCache.clear();
    } catch (RuntimeException exception) {
      LOGGER.warn("Legacy semantic collection delete failed: {}", exception.getMessage());
    }
  }

  public List<RetrievedSemanticContext> searchProjectRelatedContexts(String projectKey, String queryText, int limit) {
    return searchProjectRelatedContexts(projectKey, queryText, limit, Map.of());
  }

  public List<RetrievedSemanticContext> searchProjectRelatedContexts(
      String projectKey,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    return searchCollection("project:" + projectKey, queryText, limit, payloadFilter, () ->
        semanticVectorStoreService.searchProject(projectKey, queryText, limit, payloadFilter));
  }

  public List<RetrievedSemanticContext> searchKnowledgeContexts(String knowledgeBase, String queryText, int limit) {
    return searchKnowledgeContexts(knowledgeBase, queryText, limit, Map.of());
  }

  public List<RetrievedSemanticContext> searchKnowledgeContexts(
      String knowledgeBase,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    return searchCollection("knowledge:" + knowledgeBase, queryText, limit, payloadFilter, () ->
        semanticVectorStoreService.searchKnowledge(knowledgeBase, queryText, limit, payloadFilter));
  }

  public void invalidateTaskCache(String taskId) {
    taskContextCache.invalidate(taskId, CacheDomain.ORCHESTRATION, CacheType.TASK_CONTEXT, CacheSource.POSTGRES);
  }

  private List<RetrievedSemanticContext> searchCollection(
      String collectionKey,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter,
      SemanticSearchLoader loader
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
      LOGGER.warn(
          "Semantic search failed for collectionKey={} query='{}': {}",
          collectionKey,
          queryText,
          exception.getMessage()
      );
      return List.of();
    }
  }

  @FunctionalInterface
  private interface SemanticSearchLoader {
    List<RetrievedSemanticContext> load();
  }
}
