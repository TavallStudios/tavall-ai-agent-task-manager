package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.knowledge.KnowledgeIndexService;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.retrieval.ProjectSemanticIndexService;
import com.agenttaskmanager.app.retrieval.SemanticCollectionDomain;
import com.agenttaskmanager.app.retrieval.SemanticContextClassifier;
import com.agenttaskmanager.app.retrieval.SemanticContentType;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RetrievalCacheToolHandler extends McpToolSupport implements McpToolProvider {

  private final SharedTaskContextService sharedTaskContextService;
  private final ValidationPipelineService validationPipelineService;
  private final DashboardSummaryService dashboardSummaryService;
  private final KnowledgeIndexService knowledgeIndexService;
  private final ProjectSemanticIndexService projectSemanticIndexService;
  private final SemanticContextClassifier semanticContextClassifier;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public RetrievalCacheToolHandler(
      SharedTaskContextService sharedTaskContextService,
      ValidationPipelineService validationPipelineService,
      DashboardSummaryService dashboardSummaryService,
      KnowledgeIndexService knowledgeIndexService,
      ProjectSemanticIndexService projectSemanticIndexService,
      SemanticContextClassifier semanticContextClassifier,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.sharedTaskContextService = sharedTaskContextService;
    this.validationPipelineService = validationPipelineService;
    this.dashboardSummaryService = dashboardSummaryService;
    this.knowledgeIndexService = knowledgeIndexService;
    this.projectSemanticIndexService = projectSemanticIndexService;
    this.semanticContextClassifier = semanticContextClassifier;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "storeSemanticDocument",
            "Chunk content, embed each chunk, and store semantic payloads for a task.",
            Map.of(
                "projectKey", stringProperty("Project key."),
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "kind", stringProperty("Context kind."),
                "title", stringProperty("Document title."),
                "body", stringProperty("Text body."),
                "domain", stringProperty("Semantic domain. Optional: KNOWLEDGE_RULES, TASK_HISTORY, CODE_REPO, CHAT_ARTIFACT."),
                "contentType", stringProperty("Chunking content type. Optional: DOCUMENTATION, CHAT, CODE, DIFF, RUN_SUMMARY, GENERIC."),
                "payload", Map.of("type", "object", "description", "Payload.")
            ),
            List.of("projectKey", "taskId", "kind", "body"),
            arguments -> {
              StoreEmbeddingRequest request = map(arguments, StoreEmbeddingRequest.class);
              String embeddingId = storeProjectEmbedding(request);
              return new EmbeddingResponse(embeddingId);
            }
        ),
        spec(
            "searchSemanticChunks",
            "Search semantic chunks and return stored payload text/code, not vectors.",
            Map.of(
                "projectKey", stringProperty("Project key."),
                "queryText", stringProperty("Query text."),
                "limit", integerProperty("Result limit.")
            ),
            List.of("projectKey", "queryText"),
            arguments -> new SemanticContextResponse(search(arguments))
        ),
        spec(
            "searchSemanticHistory",
            "Search stored semantic history for related fixes, reviews, or summaries.",
            Map.of(
                "projectKey", stringProperty("Project key."),
                "queryText", stringProperty("Query text."),
                "limit", integerProperty("Result limit.")
            ),
            List.of("projectKey", "queryText"),
            arguments -> new SemanticContextResponse(search(arguments))
        ),
        spec(
            "searchKnowledgeIndex",
            "Search indexed external knowledge.",
            Map.of("queryText", stringProperty("Query text."), "limit", integerProperty("Result limit.")),
            List.of("queryText"),
            arguments -> {
              SemanticContextRequest request = map(arguments, SemanticContextRequest.class);
              int limit = request.limit() == null ? 5 : request.limit();
              return new SemanticContextResponse(knowledgeIndexService.search(request.queryText(), limit));
            }
        ),
        spec(
            "reindexSemanticKnowledge",
            "Rebuild the configured semantic knowledge index with chunk-first storage.",
            Map.of(),
            List.of(),
            arguments -> knowledgeIndexService.reindex()
        ),
        spec(
            "reindexConfiguredCodebases",
            "Rebuild semantic code/doc indexes for the configured repo allowlist.",
            Map.of(),
            List.of(),
            arguments -> projectSemanticIndexService.reindexConfiguredRepos()
        ),
        spec(
            "attachSemanticDocumentToTask",
            "Attach a semantic document to a task and index its chunked payload.",
            Map.of(
                "projectKey", stringProperty("Project key."),
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "contextKey", stringProperty("Context key."),
                "summary", stringProperty("Summary."),
                "body", stringProperty("Text body."),
                "domain", stringProperty("Semantic domain override."),
                "contentType", stringProperty("Chunking content type override.")
            ),
            List.of("projectKey", "taskId", "contextKey", "summary", "body"),
            arguments -> {
              AttachSemanticContextRequest request = map(arguments, AttachSemanticContextRequest.class);
              String embeddingId = sharedTaskContextService.storeProjectSemanticDocument(
                  requireProjectKey(request.projectKey()),
                  request.taskId(),
                  request.workerTaskId(),
                  request.contextKey(),
                  request.summary(),
                  request.body(),
                  parseDomain(request.domain(), SemanticCollectionDomain.TASK_HISTORY),
                  parseContentType(request.contentType(), SemanticContentType.RUN_SUMMARY),
                  Map.of("summary", request.summary())
              ).stream().findFirst().orElse("");
              return new EmbeddingResponse(embeddingId);
            }
        ),
        spec(
            "cacheTaskContext",
            "Warm task context cache.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            arguments -> new TaskContextResponse(sharedTaskContextService.loadTaskContext(map(arguments, TaskIdRequest.class).taskId()))
        ),
        spec(
            "getCachedTaskContext",
            "Read cached task context.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            arguments -> new TaskContextResponse(sharedTaskContextService.loadTaskContext(map(arguments, TaskIdRequest.class).taskId()))
        ),
        spec(
            "cacheValidationSummary",
            "Warm validation cache for a stored report.",
            Map.of("taskId", stringProperty("Task id."), "workerTaskId", stringProperty("Worker task id."), "repoPath", stringProperty("Repo path.")),
            List.of("taskId", "repoPath"),
            arguments -> validationPipelineService.runValidationPipeline(
                map(arguments, SpoonValidationRequest.class).taskId(),
                map(arguments, SpoonValidationRequest.class).workerTaskId(),
                java.nio.file.Path.of(map(arguments, SpoonValidationRequest.class).repoPath())
            )
        ),
        spec(
            "getCachedValidationSummary",
            "Read cached validation summary.",
            Map.of("taskId", stringProperty("Task id."), "workerTaskId", stringProperty("Worker task id.")),
            List.of("taskId"),
            arguments -> new ValidationSummaryCacheResponse(validationPipelineService.getCachedValidationSummary(
                map(arguments, ValidationRequest.class).taskId(),
                map(arguments, ValidationRequest.class).workerTaskId()
            ))
        ),
        spec(
            "invalidateTaskCache",
            "Invalidate task context cache.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            arguments -> {
              sharedTaskContextService.invalidateTaskCache(map(arguments, TaskIdRequest.class).taskId());
              return new StatusResponse("invalidated");
            }
        ),
        spec(
            "purgeLegacySemanticCollection",
            "Delete the legacy shared Qdrant collection after migrating to chunked project and knowledge collections.",
            Map.of(),
            List.of(),
            arguments -> {
              sharedTaskContextService.deleteLegacySemanticCollection();
              return new StatusResponse("deleted");
            }
        ),
        spec("warmDashboardCache", "Warm dashboard cache.", Map.of(), List.of(), arguments -> dashboardSummaryService.warmDashboardCache())
    );
  }

  private List<RetrievedSemanticContext> search(Map<String, Object> arguments) {
    SemanticContextRequest request = map(arguments, SemanticContextRequest.class);
    int limit = request.limit() == null ? 5 : request.limit();
    return sharedTaskContextService.searchProjectRelatedContexts(
        requireProjectKey(request.projectKey()),
        request.queryText(),
        limit
    );
  }

  private String storeProjectEmbedding(StoreEmbeddingRequest request) {
    Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
    SemanticCollectionDomain domain = parseDomain(request.domain(), null);
    SemanticContentType contentType = parseContentType(request.contentType(), null);
    if (domain == null || contentType == null) {
      var classification = semanticContextClassifier.classify(request.kind(), request.body(), payload);
      domain = classification.domain();
      contentType = classification.contentType();
    }
    return sharedTaskContextService.storeProjectSemanticDocument(
        requireProjectKey(request.projectKey()),
        request.taskId(),
        request.workerTaskId(),
        request.kind(),
        request.title() == null || request.title().isBlank() ? request.kind() : request.title(),
        request.body(),
        domain,
        contentType,
        payload
    ).stream().findFirst().orElse("");
  }

  private String requireProjectKey(String projectKey) {
    if (projectKey == null || projectKey.isBlank()) {
      throw new IllegalArgumentException("projectKey is required for chunked semantic retrieval.");
    }
    return projectKey.strip();
  }

  private SemanticCollectionDomain parseDomain(String rawValue, SemanticCollectionDomain fallback) {
    if (rawValue == null || rawValue.isBlank()) {
      return fallback;
    }
    return SemanticCollectionDomain.valueOf(rawValue.strip().toUpperCase(java.util.Locale.ROOT));
  }

  private SemanticContentType parseContentType(String rawValue, SemanticContentType fallback) {
    if (rawValue == null || rawValue.isBlank()) {
      return fallback;
    }
    return SemanticContentType.valueOf(rawValue.strip().toUpperCase(java.util.Locale.ROOT));
  }

  private SyncToolSpecification spec(String name, String description, Map<String, Object> properties, List<String> required, ToolCall call) {
    return new SyncToolSpecification(tool(name, description, properties, required), (exchange, request) -> resultFactory.toolResult(call.run(request.arguments())));
  }

  private <T> T map(Map<String, Object> arguments, Class<T> type) {
    return payloadMapper.map(arguments, type);
  }

  private Map<String, Object> stringProperty(String description) {
    return schemaFactory.stringProperty(description);
  }

  private Map<String, Object> integerProperty(String description) {
    return schemaFactory.integerProperty(description);
  }

  @FunctionalInterface
  private interface ToolCall {
    Object run(Map<String, Object> arguments);
  }
}
