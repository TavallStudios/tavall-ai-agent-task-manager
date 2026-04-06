package org.tavall.ai.app.retrieval;

import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.persistence.qdrant.EmbeddingPurpose;
import org.tavall.ai.app.persistence.qdrant.QdrantCollectionNameResolver;
import org.tavall.ai.app.persistence.qdrant.QdrantContextStore;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SemanticVectorStoreService {

  private final QdrantCollectionNameResolver collectionNameResolver;
  private final QdrantContextStore qdrantContextStore;
  private final SemanticRetrievalReranker semanticRetrievalReranker;
  private final SemanticQueryPlanner semanticQueryPlanner;
  private final SemanticChunkingService semanticChunkingService;

  public SemanticVectorStoreService(
      QdrantCollectionNameResolver collectionNameResolver,
      QdrantContextStore qdrantContextStore,
      SemanticRetrievalReranker semanticRetrievalReranker,
      SemanticQueryPlanner semanticQueryPlanner,
      SemanticChunkingService semanticChunkingService
  ) {
    this.collectionNameResolver = collectionNameResolver;
    this.qdrantContextStore = qdrantContextStore;
    this.semanticRetrievalReranker = semanticRetrievalReranker;
    this.semanticQueryPlanner = semanticQueryPlanner;
    this.semanticChunkingService = semanticChunkingService;
  }

  public List<String> storeProjectDocument(String projectKey, SemanticDocumentRequest request) {
    return storeChunks(collectionNameResolver.projectCollection(projectKey, request.domain()), request, Map.of("projectKey", projectKey));
  }

  public List<String> upsertKnowledgeDocument(String knowledgeBase, SemanticDocumentRequest request) {
    return storeChunks(collectionNameResolver.knowledgeCollection(knowledgeBase, request.domain()), request, Map.of("knowledgeBase", knowledgeBase));
  }

  public List<RetrievedSemanticContext> searchProject(String projectKey, String queryText, int limit, Map<String, Object> payloadFilter) {
    List<RetrievedSemanticContext> combined = new ArrayList<>();
    for (SemanticQueryPlanner.SemanticDomainSearch search : semanticQueryPlanner.planProjectQuery(queryText, limit).searches()) {
      combined.addAll(searchProject(
          projectKey,
          search.domain(),
          queryText,
          search.limit(),
          payloadFilter,
          search.embeddingPurpose()
      ));
    }
    return semanticRetrievalReranker.rerankProjectResults(
        queryText,
        dedupeAndSort(combined),
        limit,
        payloadFilter
    );
  }

  public List<RetrievedSemanticContext> searchKnowledge(
      String knowledgeBase,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    return semanticRetrievalReranker.rerankKnowledgeResults(
        queryText,
        qdrantContextStore.searchRelatedContexts(
        collectionNameResolver.knowledgeCollection(knowledgeBase, SemanticCollectionDomain.KNOWLEDGE_RULES),
        queryText,
        limit,
        withScope(payloadFilter, "knowledgeBase", knowledgeBase),
        EmbeddingPurpose.RETRIEVAL_QUERY
        ),
        limit,
        payloadFilter
    );
  }

  public void deleteProject(String projectKey, Map<String, Object> payloadFilter) {
    for (SemanticCollectionDomain domain : List.of(
        SemanticCollectionDomain.TASK_HISTORY,
        SemanticCollectionDomain.CHAT_ARTIFACT,
        SemanticCollectionDomain.CODE_REPO,
        SemanticCollectionDomain.KNOWLEDGE_RULES
    )) {
      deleteCollection(collectionNameResolver.projectCollection(projectKey, domain), withScope(payloadFilter, "projectKey", projectKey));
    }
  }

  public void deleteKnowledge(String knowledgeBase, Map<String, Object> payloadFilter) {
    deleteCollection(
        collectionNameResolver.knowledgeCollection(knowledgeBase, SemanticCollectionDomain.KNOWLEDGE_RULES),
        withScope(payloadFilter, "knowledgeBase", knowledgeBase)
    );
  }

  public void deleteLegacyCollection() {
    qdrantContextStore.deleteCollection(collectionNameResolver.legacyCollection());
  }

  private List<String> storeChunks(String collectionName, SemanticDocumentRequest request, Map<String, Object> scopePayload) {
    List<SemanticChunk> chunks = semanticChunkingService.chunk(request);
    if (chunks.isEmpty()) {
      return List.of();
    }
    String documentId = request.documentId() == null || request.documentId().isBlank()
        ? UUID.randomUUID().toString()
        : request.documentId().strip();
    List<String> pointIds = new ArrayList<>();
    for (SemanticChunk chunk : chunks) {
      String pointId = pointId(documentId, chunk.chunkIndex());
      Map<String, Object> payload = new LinkedHashMap<>(scopePayload);
      if (request.payload() != null) {
        payload.putAll(request.payload());
      }
      payload.put("documentId", documentId);
      payload.put("taskId", request.taskId());
      payload.put("workerTaskId", request.workerTaskId());
      payload.put("kind", request.kind());
      payload.put("semanticDomain", request.domain().name());
      payload.put("contentType", request.contentType().name());
      payload.put("chunkKind", chunk.chunkKind());
      payload.put("chunkIndex", chunk.chunkIndex());
      payload.put("startLine", chunk.startLine());
      payload.put("endLine", chunk.endLine());
      payload.put("title", chunk.title());
      payload.putIfAbsent("indexedAt", OffsetDateTime.now().toString());
      pointIds.add(qdrantContextStore.upsertContext(collectionName, pointId, chunk.title(), chunk.text(), payload));
    }
    return pointIds;
  }

  private List<RetrievedSemanticContext> searchProject(
      String projectKey,
      SemanticCollectionDomain domain,
      String queryText,
      int limit,
      Map<String, Object> payloadFilter,
      EmbeddingPurpose embeddingPurpose
  ) {
    return qdrantContextStore.searchRelatedContexts(
        collectionNameResolver.projectCollection(projectKey, domain),
        queryText,
        limit,
        withScope(payloadFilter, "projectKey", projectKey),
        embeddingPurpose
    );
  }

  private void deleteCollection(String collectionName, Map<String, Object> payloadFilter) {
    if (payloadFilter == null || payloadFilter.isEmpty()) {
      qdrantContextStore.deleteCollection(collectionName);
      return;
    }
    qdrantContextStore.deleteContexts(collectionName, payloadFilter);
  }

  private Map<String, Object> withScope(Map<String, Object> payloadFilter, String key, String value) {
    Map<String, Object> merged = new LinkedHashMap<>();
    if (payloadFilter != null) {
      merged.putAll(payloadFilter);
    }
    merged.put(key, value);
    return merged;
  }

  private List<RetrievedSemanticContext> dedupeAndSort(List<RetrievedSemanticContext> contexts) {
    Map<String, RetrievedSemanticContext> deduped = new LinkedHashMap<>();
    contexts.stream()
        .sorted(Comparator.comparingDouble(RetrievedSemanticContext::score).reversed())
        .forEach(item -> deduped.putIfAbsent(item.id(), item));
    return List.copyOf(deduped.values());
  }

  public static String deterministicDocumentId(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
  }

  private String pointId(String documentId, int chunkIndex) {
    if (chunkIndex == 0 && isUuid(documentId)) {
      return documentId;
    }
    return deterministicDocumentId(documentId + ":" + chunkIndex);
  }

  private boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}

