package com.agenttaskmanager.app.mcp.tools.vectormemory;

import com.agenttaskmanager.app.knowledge.KnowledgeIndexService;
import com.agenttaskmanager.app.mcp.McpJsonSchemaFactory;
import com.agenttaskmanager.app.mcp.McpResultFactory;
import com.agenttaskmanager.app.mcp.McpToolPayloadMapper;
import com.agenttaskmanager.app.mcp.McpToolProvider;
import com.agenttaskmanager.app.mcp.McpToolSupport;
import com.agenttaskmanager.app.model.PromptThreadMemoryLookupResult;
import com.agenttaskmanager.app.model.PromptThreadSummary;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.retrieval.ProjectSemanticIndexService;
import com.agenttaskmanager.app.retrieval.SemanticCollectionDomain;
import com.agenttaskmanager.app.retrieval.SemanticContextClassifier;
import com.agenttaskmanager.app.retrieval.SemanticContentType;
import com.agenttaskmanager.app.service.PromptThreadMemoryService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class VectorMemoryToolHandler extends McpToolSupport implements McpToolProvider {

  private final SharedTaskContextService sharedTaskContextService;
  private final KnowledgeIndexService knowledgeIndexService;
  private final ProjectSemanticIndexService projectSemanticIndexService;
  private final PromptThreadMemoryService promptThreadMemoryService;
  private final SemanticContextClassifier semanticContextClassifier;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public VectorMemoryToolHandler(
      SharedTaskContextService sharedTaskContextService,
      KnowledgeIndexService knowledgeIndexService,
      ProjectSemanticIndexService projectSemanticIndexService,
      PromptThreadMemoryService promptThreadMemoryService,
      SemanticContextClassifier semanticContextClassifier,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.sharedTaskContextService = sharedTaskContextService;
    this.knowledgeIndexService = knowledgeIndexService;
    this.projectSemanticIndexService = projectSemanticIndexService;
    this.promptThreadMemoryService = promptThreadMemoryService;
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
            storeProperties(),
            List.of("projectKey", "taskId", "kind", "body"),
            arguments -> new VectorMemorySearchResponse(storeProjectEmbedding(map(arguments, StoreVectorMemoryDocumentRequest.class)))
        ),
        spec(
            "searchSemanticChunks",
            "Search semantic chunks and return stored payload text/code, not vectors.",
            semanticQueryProperties(),
            List.of("projectKey", "queryText"),
            arguments -> new VectorMemorySemanticContextResponse(search(arguments))
        ),
        spec(
            "searchSemanticHistory",
            "Search stored semantic history for related fixes, reviews, or summaries.",
            semanticQueryProperties(),
            List.of("projectKey", "queryText"),
            arguments -> new VectorMemorySemanticContextResponse(search(arguments))
        ),
        spec(
            "searchPromptThreads",
            "Search durable prompt threads by chat key or recent prompt text.",
            Map.of("queryText", stringProperty("Chat key or prompt text query."), "limit", integerProperty("Result limit.")),
            List.of("queryText"),
            arguments -> new PromptThreadSearchResponse(searchPromptThreads(arguments))
        ),
        spec(
            "searchPromptThreadMemory",
            "Search semantic memory for one durable prompt thread key.",
            Map.of(
                "projectKey", stringProperty("Project key."),
                "threadKey", stringProperty("Prompt thread key."),
                "queryText", stringProperty("Query text."),
                "limit", integerProperty("Optional thread search limit for future use.")
            ),
            List.of("projectKey", "threadKey", "queryText"),
            arguments -> new PromptThreadMemoryResponse(searchPromptThreadMemory(arguments))
        ),
        spec(
            "searchKnowledgeIndex",
            "Search indexed external knowledge.",
            Map.of("queryText", stringProperty("Query text."), "limit", integerProperty("Result limit.")),
            List.of("queryText"),
            arguments -> searchKnowledge(arguments)
        ),
        spec("reindexSemanticKnowledge", "Rebuild the configured semantic knowledge index with chunk-first storage.", Map.of(), List.of(), arguments -> knowledgeIndexService.reindex()),
        spec("reindexConfiguredCodebases", "Rebuild semantic code/doc indexes for the configured repo allowlist.", Map.of(), List.of(), arguments -> projectSemanticIndexService.reindexConfiguredRepos()),
        spec(
            "attachSemanticDocumentToTask",
            "Attach a semantic document to a task and index its chunked payload.",
            attachProperties(),
            List.of("projectKey", "taskId", "contextKey", "summary", "body"),
            arguments -> new VectorMemorySearchResponse(attachSemanticDocument(map(arguments, AttachVectorMemoryDocumentRequest.class)))
        ),
        spec(
            "purgeLegacySemanticCollection",
            "Delete the legacy shared Qdrant collection after migrating to chunked project and knowledge collections.",
            Map.of(),
            List.of(),
            arguments -> purgeLegacyCollection()
        )
    );
  }

  private VectorMemorySemanticContextResponse searchKnowledge(Map<String, Object> arguments) {
    VectorMemorySemanticQueryRequest request = map(arguments, VectorMemorySemanticQueryRequest.class);
    int limit = request.limit() == null ? 5 : request.limit();
    return new VectorMemorySemanticContextResponse(knowledgeIndexService.search(request.queryText(), limit));
  }

  private List<PromptThreadSummary> searchPromptThreads(Map<String, Object> arguments) {
    PromptThreadMemoryQueryRequest request = map(arguments, PromptThreadMemoryQueryRequest.class);
    int limit = request.limit() == null ? 10 : request.limit();
    return promptThreadMemoryService.searchThreads(request.queryText(), limit);
  }

  private PromptThreadMemoryLookupResult searchPromptThreadMemory(Map<String, Object> arguments) {
    PromptThreadMemoryQueryRequest request = map(arguments, PromptThreadMemoryQueryRequest.class);
    return promptThreadMemoryService.lookup(
        requireProjectKey(request.projectKey()),
        request.threadKey(),
        request.queryText()
    );
  }

  private List<RetrievedSemanticContext> search(Map<String, Object> arguments) {
    VectorMemorySemanticQueryRequest request = map(arguments, VectorMemorySemanticQueryRequest.class);
    return sharedTaskContextService.searchProjectRelatedContexts(
        requireProjectKey(request.projectKey()),
        request.queryText(),
        request.limit() == null ? 5 : request.limit()
    );
  }

  private String attachSemanticDocument(AttachVectorMemoryDocumentRequest request) {
    return sharedTaskContextService.storeProjectSemanticDocument(
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
  }

  private String storeProjectEmbedding(StoreVectorMemoryDocumentRequest request) {
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

  private Object purgeLegacyCollection() {
    sharedTaskContextService.deleteLegacySemanticCollection();
    return new VectorMemoryStatusResponse("deleted");
  }

  private String requireProjectKey(String projectKey) {
    if (projectKey == null || projectKey.isBlank()) {
      throw new IllegalArgumentException("projectKey is required for vector memory retrieval.");
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

  private Map<String, Object> storeProperties() {
    return Map.of(
        "projectKey", stringProperty("Project key."),
        "taskId", stringProperty("Task id."),
        "workerTaskId", stringProperty("Worker task id."),
        "kind", stringProperty("Context kind."),
        "title", stringProperty("Document title."),
        "body", stringProperty("Text body."),
        "domain", stringProperty("Semantic domain. Optional: KNOWLEDGE_RULES, TASK_HISTORY, CODE_REPO, CHAT_ARTIFACT."),
        "contentType", stringProperty("Chunking content type. Optional: DOCUMENTATION, CHAT, CODE, DIFF, RUN_SUMMARY, GENERIC."),
        "payload", Map.of("type", "object", "description", "Payload.")
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
    return Map.of(
        "projectKey", stringProperty("Project key."),
        "taskId", stringProperty("Task id."),
        "workerTaskId", stringProperty("Worker task id."),
        "contextKey", stringProperty("Context key."),
        "summary", stringProperty("Summary."),
        "body", stringProperty("Text body."),
        "domain", stringProperty("Semantic domain override."),
        "contentType", stringProperty("Chunking content type override.")
    );
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

record VectorMemoryStatusResponse(String status) {
}

record PromptThreadSearchResponse(List<PromptThreadSummary> items) {
}

record PromptThreadMemoryResponse(PromptThreadMemoryLookupResult item) {
}
