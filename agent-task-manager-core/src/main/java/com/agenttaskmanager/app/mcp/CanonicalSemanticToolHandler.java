package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.model.orchestration.SharedTaskContext;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.retrieval.SemanticCollectionDomain;
import com.agenttaskmanager.app.retrieval.SemanticContentType;
import com.agenttaskmanager.app.retrieval.SemanticContextClassifier;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CanonicalSemanticToolHandler extends McpToolSupport implements McpToolProvider {

  private final SharedTaskContextService sharedTaskContextService;
  private final SemanticContextClassifier semanticContextClassifier;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public CanonicalSemanticToolHandler(
      SharedTaskContextService sharedTaskContextService,
      SemanticContextClassifier semanticContextClassifier,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.sharedTaskContextService = sharedTaskContextService;
    this.semanticContextClassifier = semanticContextClassifier;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "storeTaskEmbedding",
            "Chunk content, embed the chunks, and store payload plus metadata in Qdrant.",
            semanticDocumentProperties(),
            List.of("projectKey", "taskId", "kind", "body"),
            arguments -> new CanonicalEmbeddingResponse(storeTaskEmbedding(arguments))
        ),
        spec(
            "searchRelatedContexts",
            "Search chunked semantic context and return stored payload text/code, not vectors.",
            semanticQueryProperties(),
            List.of("projectKey", "queryText"),
            arguments -> new CanonicalSemanticContextResponse(searchRelatedContexts(arguments))
        ),
        spec(
            "loadRelatedSemanticContext",
            "Load chunked semantic context related to a query for the active project.",
            semanticQueryProperties(),
            List.of("projectKey", "queryText"),
            arguments -> new CanonicalSemanticContextResponse(searchRelatedContexts(arguments))
        ),
        spec(
            "searchPriorFixes",
            "Search prior fix and review history stored in semantic task history collections.",
            semanticQueryProperties(),
            List.of("projectKey", "queryText"),
            arguments -> new CanonicalSemanticContextResponse(searchPriorFixes(arguments))
        ),
        spec(
            "attachSemanticContextToTask",
            "Attach shared task context and index the same body through the chunk-first semantic pipeline.",
            attachProperties(),
            List.of("projectKey", "taskId", "contextKey", "summary", "body"),
            arguments -> attachSemanticContextToTask(arguments)
        )
    );
  }

  private CanonicalTaskSemanticAttachmentResponse attachSemanticContextToTask(Map<String, Object> arguments) {
    CanonicalAttachSemanticContextRequest request = map(arguments, CanonicalAttachSemanticContextRequest.class);
    Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
    SharedTaskContext context = sharedTaskContextService.storeSharedTaskContext(
        request.taskId(),
        request.workerTaskId(),
        request.contextKey(),
        request.visibility() == null || request.visibility().isBlank() ? "team" : request.visibility().strip(),
        request.summary(),
        payload
    );
    List<String> pointIds = sharedTaskContextService.storeProjectSemanticDocument(
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
    return new CanonicalTaskSemanticAttachmentResponse(context, pointIds);
  }

  private List<RetrievedSemanticContext> searchPriorFixes(Map<String, Object> arguments) {
    CanonicalSemanticQueryRequest request = map(arguments, CanonicalSemanticQueryRequest.class);
    int limit = request.limit() == null ? 5 : request.limit();
    return sharedTaskContextService.searchProjectRelatedContexts(
        requireProjectKey(request.projectKey()),
        request.queryText(),
        limit,
        Map.of("semanticDomain", SemanticCollectionDomain.TASK_HISTORY.name())
    );
  }

  private List<RetrievedSemanticContext> searchRelatedContexts(Map<String, Object> arguments) {
    CanonicalSemanticQueryRequest request = map(arguments, CanonicalSemanticQueryRequest.class);
    int limit = request.limit() == null ? 5 : request.limit();
    return sharedTaskContextService.searchProjectRelatedContexts(
        requireProjectKey(request.projectKey()),
        request.queryText(),
        limit
    );
  }

  private String storeTaskEmbedding(Map<String, Object> arguments) {
    CanonicalStoreTaskEmbeddingRequest request = map(arguments, CanonicalStoreTaskEmbeddingRequest.class);
    Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
    return sharedTaskContextService.storeProjectSemanticDocument(
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

  private SemanticCollectionDomain resolveDomain(
      String kind,
      String body,
      Map<String, Object> payload,
      String rawValue
  ) {
    if (rawValue != null && !rawValue.isBlank()) {
      return SemanticCollectionDomain.valueOf(rawValue.strip().toUpperCase(Locale.ROOT));
    }
    return semanticContextClassifier.classify(kind, body, payload).domain();
  }

  private SemanticContentType resolveContentType(
      String kind,
      String body,
      Map<String, Object> payload,
      String rawValue
  ) {
    if (rawValue != null && !rawValue.isBlank()) {
      return SemanticContentType.valueOf(rawValue.strip().toUpperCase(Locale.ROOT));
    }
    return semanticContextClassifier.classify(kind, body, payload).contentType();
  }

  private String requireProjectKey(String projectKey) {
    if (projectKey == null || projectKey.isBlank()) {
      throw new IllegalArgumentException("projectKey is required for canonical semantic retrieval.");
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
        "body", stringProperty("Raw text or code body."),
        "domain", stringProperty("Semantic domain override."),
        "contentType", stringProperty("Chunking content type override."),
        "payload", Map.of("type", "object", "description", "Chunk metadata payload.")
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
    properties.put("summary", stringProperty("Context summary."));
    properties.put("body", stringProperty("Body to chunk and index."));
    properties.put("kind", stringProperty("Context kind override."));
    properties.put("domain", stringProperty("Semantic domain override."));
    properties.put("contentType", stringProperty("Chunking content type override."));
    properties.put("payload", Map.of("type", "object", "description", "Chunk metadata payload."));
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

record CanonicalStoreTaskEmbeddingRequest(
    String projectKey,
    String taskId,
    String workerTaskId,
    String kind,
    String title,
    String body,
    String domain,
    String contentType,
    Map<String, Object> payload
) {
}

record CanonicalSemanticQueryRequest(String projectKey, String queryText, Integer limit) {
}

record CanonicalAttachSemanticContextRequest(
    String projectKey,
    String taskId,
    String workerTaskId,
    String contextKey,
    String visibility,
    String summary,
    String body,
    String kind,
    String domain,
    String contentType,
    Map<String, Object> payload
) {
}

record CanonicalEmbeddingResponse(String embeddingId) {
}

record CanonicalSemanticContextResponse(List<RetrievedSemanticContext> items) {
}

record CanonicalTaskSemanticAttachmentResponse(SharedTaskContext context, List<String> pointIds) {
}
