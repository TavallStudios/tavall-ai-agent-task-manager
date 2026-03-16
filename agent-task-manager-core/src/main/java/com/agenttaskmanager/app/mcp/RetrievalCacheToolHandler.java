package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.knowledge.KnowledgeIndexService;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
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
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public RetrievalCacheToolHandler(
      SharedTaskContextService sharedTaskContextService,
      ValidationPipelineService validationPipelineService,
      DashboardSummaryService dashboardSummaryService,
      KnowledgeIndexService knowledgeIndexService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.sharedTaskContextService = sharedTaskContextService;
    this.validationPipelineService = validationPipelineService;
    this.dashboardSummaryService = dashboardSummaryService;
    this.knowledgeIndexService = knowledgeIndexService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "storeTaskEmbedding",
            "Store vector context for a task.",
            Map.of(
                "projectKey", stringProperty("Project key. Leave blank only for legacy collection access."),
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "kind", stringProperty("Context kind."),
                "body", stringProperty("Text body."),
                "payload", Map.of("type", "object", "description", "Payload.")
            ),
            List.of("taskId", "kind", "body"),
            arguments -> {
              StoreEmbeddingRequest request = map(arguments, StoreEmbeddingRequest.class);
              String embeddingId = request.projectKey() == null || request.projectKey().isBlank()
                  ? sharedTaskContextService.storeTaskEmbedding(
                      request.taskId(),
                      request.workerTaskId(),
                      request.kind(),
                      request.body(),
                      request.payload() == null ? Map.of() : request.payload()
                  )
                  : sharedTaskContextService.storeTaskEmbedding(
                      request.projectKey(),
                      request.taskId(),
                      request.workerTaskId(),
                      request.kind(),
                      request.body(),
                      request.payload() == null ? Map.of() : request.payload()
                  );
              return new EmbeddingResponse(embeddingId);
            }
        ),
        spec(
            "searchRelatedContexts",
            "Search semantic contexts.",
            Map.of(
                "projectKey", stringProperty("Project key. Leave blank only for legacy collection access."),
                "queryText", stringProperty("Query text."),
                "limit", integerProperty("Result limit.")
            ),
            List.of("queryText"),
            arguments -> new SemanticContextResponse(search(arguments))
        ),
        spec(
            "searchPriorFixes",
            "Search prior fixes from semantic context.",
            Map.of(
                "projectKey", stringProperty("Project key. Leave blank only for legacy collection access."),
                "queryText", stringProperty("Query text."),
                "limit", integerProperty("Result limit.")
            ),
            List.of("queryText"),
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
            "reindexKnowledgeIndex",
            "Rebuild the configured semantic knowledge index.",
            Map.of(),
            List.of(),
            arguments -> knowledgeIndexService.reindex()
        ),
        spec(
            "attachSemanticContextToTask",
            "Attach semantic context to a task via shared storage.",
            Map.of(
                "projectKey", stringProperty("Project key. Leave blank only for legacy collection access."),
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "contextKey", stringProperty("Context key."),
                "summary", stringProperty("Summary."),
                "body", stringProperty("Text body.")
            ),
            List.of("taskId", "contextKey", "summary", "body"),
            arguments -> {
              AttachSemanticContextRequest request = map(arguments, AttachSemanticContextRequest.class);
              String embeddingId = request.projectKey() == null || request.projectKey().isBlank()
                  ? sharedTaskContextService.storeTaskEmbedding(
                      request.taskId(),
                      request.workerTaskId(),
                      request.contextKey(),
                      request.body(),
                      Map.of("summary", request.summary())
                  )
                  : sharedTaskContextService.storeTaskEmbedding(
                      request.projectKey(),
                      request.taskId(),
                      request.workerTaskId(),
                      request.contextKey(),
                      request.body(),
                      Map.of("summary", request.summary())
                  );
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
            "dropLegacyVectorCollection",
            "Delete the legacy shared Qdrant collection now that project collections are in place.",
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
    return request.projectKey() == null || request.projectKey().isBlank()
        ? sharedTaskContextService.searchRelatedContexts(request.queryText(), limit)
        : sharedTaskContextService.searchProjectRelatedContexts(request.projectKey(), request.queryText(), limit);
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

record StoreEmbeddingRequest(String projectKey, String taskId, String workerTaskId, String kind, String body, Map<String, Object> payload) {
}

record AttachSemanticContextRequest(String projectKey, String taskId, String workerTaskId, String contextKey, String summary, String body) {
}

record EmbeddingResponse(String embeddingId) {
}

record ValidationSummaryCacheResponse(Map<String, Object> payload) {
}
