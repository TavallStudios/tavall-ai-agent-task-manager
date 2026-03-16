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
import com.agenttaskmanager.app.persistence.qdrant.QdrantCollectionNameResolver;
import com.agenttaskmanager.app.persistence.qdrant.QdrantContextStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class SharedTaskContextService {

  private final SharedTaskContextRepository sharedTaskContextRepository;
  private final WorkerTaskRepository workerTaskRepository;
  private final QdrantContextStore qdrantContextStore;
  private final QdrantCollectionNameResolver collectionNameResolver;
  private final TaskContextCache taskContextCache;
  private final SemanticContextCache semanticContextCache;

  public SharedTaskContextService(
      SharedTaskContextRepository sharedTaskContextRepository,
      WorkerTaskRepository workerTaskRepository,
      QdrantContextStore qdrantContextStore,
      QdrantCollectionNameResolver collectionNameResolver,
      TaskContextCache taskContextCache,
      SemanticContextCache semanticContextCache
  ) {
    this.sharedTaskContextRepository = sharedTaskContextRepository;
    this.workerTaskRepository = workerTaskRepository;
    this.qdrantContextStore = qdrantContextStore;
    this.collectionNameResolver = collectionNameResolver;
    this.taskContextCache = taskContextCache;
    this.semanticContextCache = semanticContextCache;
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
          List<SharedTaskContext> contexts = sharedTaskContextRepository.listByTask(taskId);
          List<WorkerTask> workerTasks = workerTaskRepository.listWorkerTasks(taskId);
          Map<String, Object> payload = new LinkedHashMap<>();
          payload.put("taskId", taskId);
          payload.put("contexts", contexts);
          payload.put("workerTasks", workerTasks);
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

  public String storeTaskEmbedding(
      String projectKey,
      String taskId,
      String workerTaskId,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    String pointId = qdrantContextStore.storeContext(
        collectionNameResolver.projectCollection(projectKey),
        taskId,
        workerTaskId,
        kind,
        body,
        mergeScopePayload(payload, "projectKey", projectKey)
    );
    semanticContextCache.clear();
    return pointId;
  }

  public String storeTaskEmbedding(
      String taskId,
      String workerTaskId,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    String pointId = qdrantContextStore.storeContext(taskId, workerTaskId, kind, body, payload);
    semanticContextCache.clear();
    return pointId;
  }

  public String upsertProjectSemanticContext(
      String projectKey,
      String pointId,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    String storedPointId = qdrantContextStore.upsertContext(
        collectionNameResolver.projectCollection(projectKey),
        pointId,
        kind,
        body,
        mergeScopePayload(payload, "projectKey", projectKey)
    );
    semanticContextCache.clear();
    return storedPointId;
  }

  public String upsertKnowledgeContext(
      String knowledgeBase,
      String pointId,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    String storedPointId = qdrantContextStore.upsertContext(
        collectionNameResolver.knowledgeCollection(knowledgeBase),
        pointId,
        kind,
        body,
        mergeScopePayload(payload, "knowledgeBase", knowledgeBase)
    );
    semanticContextCache.clear();
    return storedPointId;
  }

  public String upsertSemanticContext(String pointId, String kind, String body, Map<String, Object> payload) {
    String storedPointId = qdrantContextStore.upsertContext(pointId, kind, body, payload);
    semanticContextCache.clear();
    return storedPointId;
  }

  public void deleteProjectSemanticContexts(String projectKey, Map<String, Object> payloadFilter) {
    deleteScopedContexts(
        collectionNameResolver.projectCollection(projectKey),
        mergeScopePayload(payloadFilter, "projectKey", projectKey)
    );
  }

  public void deleteKnowledgeContexts(String knowledgeBase, Map<String, Object> payloadFilter) {
    deleteScopedContexts(
        collectionNameResolver.knowledgeCollection(knowledgeBase),
        mergeScopePayload(payloadFilter, "knowledgeBase", knowledgeBase)
    );
  }

  public void deleteSemanticContexts(Map<String, Object> payloadFilter) {
    qdrantContextStore.deleteContexts(payloadFilter);
    semanticContextCache.clear();
  }

  public void deleteLegacySemanticCollection() {
    qdrantContextStore.deleteCollection(collectionNameResolver.legacyCollection());
    semanticContextCache.clear();
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
    return searchCollection(
        collectionNameResolver.projectCollection(projectKey),
        queryText,
        limit,
        mergeScopePayload(payloadFilter, "projectKey", projectKey)
    );
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
    return searchCollection(
        collectionNameResolver.knowledgeCollection(knowledgeBase),
        queryText,
        limit,
        mergeScopePayload(payloadFilter, "knowledgeBase", knowledgeBase)
    );
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(String queryText, int limit) {
    return searchRelatedContexts(queryText, limit, Map.of());
  }

  public List<RetrievedSemanticContext> searchRelatedContexts(String queryText, int limit, Map<String, Object> payloadFilter) {
    return searchCollection(collectionNameResolver.legacyCollection(), queryText, limit, payloadFilter);
  }

  public void invalidateTaskCache(String taskId) {
    taskContextCache.invalidate(
        taskId,
        CacheDomain.ORCHESTRATION,
        CacheType.TASK_CONTEXT,
        CacheSource.POSTGRES
    );
  }

  private List<RetrievedSemanticContext> searchCollection(
      String collectionName,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    Map<String, Object> normalizedFilter = payloadFilter == null ? Map.of() : new TreeMap<>(payloadFilter);
    List<Map<String, Object>> cached = semanticContextCache.getOrLoad(
        buildSemanticCacheKey(collectionName, queryText, limit, normalizedFilter),
        CacheDomain.RETRIEVAL,
        CacheType.SEMANTIC_CONTEXT,
        CacheSource.QDRANT,
        () -> qdrantContextStore.searchRelatedContexts(collectionName, queryText, limit, normalizedFilter).stream()
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
  }

  private void deleteScopedContexts(String collectionName, Map<String, Object> payloadFilter) {
    if (payloadFilter == null || payloadFilter.isEmpty()) {
      qdrantContextStore.deleteCollection(collectionName);
    } else {
      qdrantContextStore.deleteContexts(collectionName, payloadFilter);
    }
    semanticContextCache.clear();
  }

  private static String buildSemanticCacheKey(
      String collectionName,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    return collectionName + ":" + queryText + ":" + limit + ":" + payloadFilter;
  }

  private static Map<String, Object> mergeScopePayload(Map<String, Object> payload, String scopeKey, String scopeValue) {
    Map<String, Object> merged = new LinkedHashMap<>();
    if (payload != null) {
      merged.putAll(payload);
    }
    if (scopeValue != null && !scopeValue.isBlank()) {
      merged.putIfAbsent(scopeKey, scopeValue);
    }
    return merged;
  }
}
