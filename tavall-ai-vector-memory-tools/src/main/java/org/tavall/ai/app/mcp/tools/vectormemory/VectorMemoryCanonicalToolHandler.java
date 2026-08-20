package org.tavall.ai.app.mcp.tools.vectormemory;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.tavall.ai.app.mcp.McpJsonSchemaFactory;
import org.tavall.ai.app.mcp.McpResultFactory;
import org.tavall.ai.app.mcp.McpToolPayloadMapper;
import org.tavall.ai.app.mcp.McpToolProvider;
import org.tavall.ai.app.mcp.McpToolSupport;
import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.model.orchestration.SharedTaskContext;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import org.tavall.ai.app.retrieval.SemanticCollectionDomain;
import org.tavall.ai.app.retrieval.SemanticContentType;
import org.tavall.ai.app.retrieval.SemanticContextClassifier;
import org.tavall.ai.app.retrieval.SemanticMemoryService;

@Component
public class VectorMemoryCanonicalToolHandler extends McpToolSupport implements McpToolProvider {

  private final SharedTaskContextService sharedTaskContextService;
  private final SemanticMemoryService semanticMemoryService;
  private final SemanticContextClassifier semanticContextClassifier;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public VectorMemoryCanonicalToolHandler(
      SharedTaskContextService sharedTaskContextService,
      SemanticMemoryService semanticMemoryService,
      SemanticContextClassifier semanticContextClassifier,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.sharedTaskContextService = sharedTaskContextService;
    this.semanticMemoryService = semanticMemoryService;
    this.semanticContextClassifier = semanticContextClassifier;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "storeTaskEmbedding",
            "Chunk content, embed the chunks, and store payload plus metadata in semantic memory.",
            semanticDocumentProperties(),
            List.of("projectKey", "taskId", "kind", "body"),
            arguments -> new VectorMemoryEmbeddingResponse(storeTaskEmbedding(arguments))
        ),
        spec(
            "searchRelatedContexts",
            "Search focused semantic context and return stored payload text/code, not vectors.",
            semanticQueryProperties(),
            List.of("projectKey", "queryText"),
            arguments -> new VectorMemorySemanticContextResponse(searchRelatedContexts(arguments))
        ),
        spec(
            "searchPriorFixes",
            "Search prior fix and review history stored in semantic task-history collections.",
            semanticQueryProperties(),
            List.of("projectKey", "queryText"),
            arguments -> new VectorMemorySemanticContextResponse(searchPriorFixes(arguments))
        ),
        spec(
            "attachSemanticContextToTask",
            "Attach structured shared task context and explicitly index the supplied distilled body into semantic memory.",
            attachProperties(),
            List.of("projectKey", "taskId", "contextKey", "summary", "body"),
            arguments -> attachSemanticContextToTask(arguments)
        )
    );
  }

  private VectorMemoryTaskSemanticAttachmentResponse attachSemanticContextToTask(Map<String, Object> arguments) {
    VectorMemoryAttachSemanticContextRequest request = map(arguments, VectorMemoryAttachSemanticContextRequest.class);
    Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
    SharedTaskContext context = sharedTaskContextService.storeSharedTaskContext(
        request.taskId(),
        request.workerTaskId(),
        request.contextKey(),
        request.visibility() == null || request.visibility().isBlank() ? "team" : request.visibility().strip(),
        request.summary(),
        payload
    );
    List<String> pointIds = semanticMemoryService.storeProjectDocument(
        requireProjectKey(request.projectKey()),
        request.taskId(),
        request.workerTaskId(),
        request.contextKey(),
        request.summary(),
        request.body(),
        resolveDomain(request.kind(), request.body(), payload, request.domain()),
        resolveContentType(request.kind(), request.body(), payload, request.contentType()),
        payload
    );
    return new VectorMemoryTaskSemanticAttachmentResponse(context, pointIds);
  }

  private List<RetrievedSemanticContext> searchPriorFixes(Map<String, Object> arguments) {
    VectorMemorySemanticQueryRequest request = map(arguments, VectorMemorySemanticQueryRequest.class);
    return semanticMemoryService.searchProject(
        requireProjectKey(request.projectKey()),
        request.queryText(),
        normalizeLimit(request.limit()),
        Map.of("semanticDomain", SemanticCollectionDomain.TASK_HISTORY.name())
    );
  }

  private List<RetrievedSemanticContext> searchRelatedContexts(Map<String, Object> arguments) {
    VectorMemorySemanticQueryRequest request = map(arguments, VectorMemorySemanticQueryRequest.class);
    return semanticMemoryService.searchProject(
        requireProjectKey(request.projectKey()),
        request.queryText(),
        normalizeLimit(request.limit()),
        Map.of()
    );
  }

  private String storeTaskEmbedding(Map<String, Object> arguments) {
    VectorMemoryStoreTaskEmbeddingRequest request = map(arguments, VectorMemoryStoreTaskEmbeddingRequest.class);
    Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
    return semanticMemoryService.storeProjectDocument(
        requireProjectKey(request.projectKey()),
        request.taskId(),
        request.workerTaskId(),
        request.kind(),
        request.title() == null || request.title().isBlank() ? request.kind() : request.title(),
        request.body(),
        resolveDomain(request.kind(), request.body(), payload, request.domain()),
        resolveContentType(request.kind(), request.body(), payload, request.contentType()),
        payload
    ).stream().findFirst().orElse("");
  }

  private SemanticCollectionDomain resolveDomain(String kind, String body, Map<String, Object> payload, String rawValue) {
    if (rawValue != null && !rawValue.isBlank()) {
      return SemanticCollectionDomain.valueOf(rawValue.strip().toUpperCase(Locale.ROOT));
    }
    return semanticContextClassifier.classify(kind, body, payload).domain();
  }

  private SemanticContentType resolveContentType(String kind, String body, Map<String, Object> payload, String rawValue) {
    if (rawValue != null && !rawValue.isBlank()) {
      return SemanticContentType.valueOf(rawValue.strip().toUpperCase(Locale.ROOT));
    }
    return semanticContextClassifier.classify(kind, body, payload).contentType();
  }

  private int normalizeLimit(Integer limit) {
    return limit == null ? 5 : Math.max(1, Math.min(50, limit));
  }

  private String requireProjectKey(String projectKey) {
    if (projectKey == null || projectKey.isBlank()) {
      throw new IllegalArgumentException("projectKey is required for vector memory retrieval.");
    }
    return projectKey.strip();
  }

  private Map<String, Object> semanticDocumentProperties() {
    return Map.of(
        "projectKey", stringProperty("Project key."),
        "taskId", stringProperty("Task id."),
        "workerTaskId", stringProperty("Worker task id."),
        "kind", stringProperty("Context kind."),
        "title", stringProperty("Document title."),
        "body", stringProperty("Distilled text or code body to index."),
        "domain", stringProperty("Semantic domain override."),
        "contentType", stringProperty("Chunking content type override."),
        "payload", Map.of("type", "object", "description", "Chunk metadata and provenance payload.")
    );
  }

  private Map<String, Object> semanticQueryProperties() {
    return Map.of(
        "projectKey", stringProperty("Project key."),
        "queryText", stringProperty("Query text."),
        "limit", integerProperty("Result limit.")
    );
  }

  private Map<String, Object> attachProperties() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("projectKey", stringProperty("Project key."));
    properties.put("taskId", stringProperty("Task id."));
    properties.put("workerTaskId", stringProperty("Worker task id."));
    properties.put("contextKey", stringProperty("Context key."));
    properties.put("visibility", stringProperty("Context visibility."));
    properties.put("summary", stringProperty("Structured task-context summary."));
    properties.put("body", stringProperty("Distilled body to index explicitly."));
    properties.put("kind", stringProperty("Context kind override."));
    properties.put("domain", stringProperty("Semantic domain override."));
    properties.put("contentType", stringProperty("Chunking content type override."));
    properties.put("payload", Map.of("type", "object", "description", "Chunk metadata and provenance payload."));
    return properties;
  }

  private SyncToolSpecification spec(
      String name,
      String description,
      Map<String, Object> properties,
      List<String> required,
      ToolCall call
  ) {
    return new SyncToolSpecification(
        tool(name, description, properties, required),
        (exchange, request) -> resultFactory.toolResult(call.run(request.arguments()))
    );
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
