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
import org.tavall.ai.app.memory.GraphifyCodeKnowledgeProvider;
import org.tavall.ai.app.memory.GraphitiTemporalKnowledgeProvider;
import org.tavall.ai.app.memory.MemoryIdentity;
import org.tavall.ai.app.memory.MemoryKind;
import org.tavall.ai.app.memory.MemoryKnowledgeContext;
import org.tavall.ai.app.memory.MemoryKnowledgeQuery;
import org.tavall.ai.app.memory.MemoryProviderTelemetryService;
import org.tavall.ai.app.memory.MemoryRecordService;
import org.tavall.ai.app.memory.MemoryRetrievalService;
import org.tavall.ai.app.memory.MemoryScope;
import org.tavall.ai.app.memory.MemoryWriteRequest;

@Component
public class MemoryKnowledgeToolHandler extends McpToolSupport implements McpToolProvider {

  private final GraphifyCodeKnowledgeProvider graphifyProvider;
  private final GraphitiTemporalKnowledgeProvider graphitiProvider;
  private final MemoryProviderTelemetryService telemetryService;
  private final MemoryRecordService memoryRecordService;
  private final MemoryRetrievalService memoryRetrievalService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public MemoryKnowledgeToolHandler(
      GraphifyCodeKnowledgeProvider graphifyProvider,
      GraphitiTemporalKnowledgeProvider graphitiProvider,
      MemoryProviderTelemetryService telemetryService,
      MemoryRecordService memoryRecordService,
      MemoryRetrievalService memoryRetrievalService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.graphifyProvider = graphifyProvider;
    this.graphitiProvider = graphitiProvider;
    this.telemetryService = telemetryService;
    this.memoryRecordService = memoryRecordService;
    this.memoryRetrievalService = memoryRetrievalService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  /** Exposes provider-neutral memory hydration plus explicit durable and focused provider operations. */
  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "memoryContext",
            "Compile exact, semantic, structural, and temporal Tavall memory for one agent query.",
            memoryContextProperties(),
            List.of("projectId", "queryText"),
            this::memoryContext
        ),
        spec(
            "recordMemory",
            "Persist one intentional distilled Tavall memory with provenance; ordinary turns are never stored automatically.",
            memoryRecordProperties(),
            List.of("projectId", "title", "summary"),
            this::recordMemory
        ),
        spec(
            "memoryRelated",
            "Retrieve current code structure from the configured Graphify provider.",
            knowledgeQueryProperties(),
            List.of("queryText"),
            this::structuralContext
        ),
        spec(
            "codeImpact",
            "Retrieve Graphify pull-request blast radius against the current code graph.",
            codeImpactProperties(),
            List.of("pullRequestNumber"),
            this::codeImpact
        ),
        spec(
            "memoryHistory",
            "Retrieve temporal facts and relationship history from the configured Graphiti provider.",
            knowledgeQueryProperties(),
            List.of("queryText"),
            this::temporalContext
        ),
        spec(
            "recordTemporalFact",
            "Record one already-verified temporal relationship without LLM fact extraction.",
            temporalFactProperties(),
            List.of("sourceNode", "edgeName", "fact", "targetNode"),
            this::recordTemporalFact
        ),
        spec(
            "memoryProviderStats",
            "Return process-local retrieval latency, degradation, and context-volume statistics by memory provider.",
            Map.of(),
            List.of(),
            arguments -> telemetryService.snapshot()
        )
    );
  }

  private Object memoryContext(Map<String, Object> arguments) {
    MemoryContextToolRequest request = map(arguments, MemoryContextToolRequest.class);
    return memoryRetrievalService.lookup(
        request.projectId(),
        request.threadKey(),
        request.sessionId(),
        request.requestedBy(),
        request.requestedFrom(),
        request.repoPath(),
        request.queryText(),
        request.metadata() == null ? Map.of() : request.metadata()
    );
  }

  private Object recordMemory(Map<String, Object> arguments) {
    MemoryRecordToolRequest request = map(arguments, MemoryRecordToolRequest.class);
    Map<String, Object> metadata = request.metadata() == null ? Map.of() : request.metadata();
    MemoryIdentity identity = memoryRetrievalService.resolveIdentity(
        request.projectId(),
        request.threadKey(),
        request.sessionId(),
        request.requestedBy(),
        request.requestedFrom(),
        request.repoPath(),
        metadata
    );
    return memoryRecordService.record(identity, new MemoryWriteRequest(
        parseScope(request.scope()),
        parseKind(request.kind()),
        request.title(),
        request.summary(),
        request.facts(),
        request.importance(),
        request.sensitivity(),
        request.consentLevel(),
        request.sourceReference(),
        request.supersedesMemoryId(),
        metadata
    ));
  }

  private Object structuralContext(Map<String, Object> arguments) {
    MemoryKnowledgeToolRequest request = map(arguments, MemoryKnowledgeToolRequest.class);
    return record(graphifyProvider.retrieve(query(request)));
  }

  private Object codeImpact(Map<String, Object> arguments) {
    CodeImpactToolRequest request = map(arguments, CodeImpactToolRequest.class);
    int pullRequestNumber = request.pullRequestNumber() == null ? 0 : request.pullRequestNumber();
    return record(graphifyProvider.inspectPullRequest(request.repository(), pullRequestNumber, request.repoPath()));
  }

  private Object temporalContext(Map<String, Object> arguments) {
    MemoryKnowledgeToolRequest request = map(arguments, MemoryKnowledgeToolRequest.class);
    return record(graphitiProvider.retrieve(query(request)));
  }

  private Object recordTemporalFact(Map<String, Object> arguments) {
    TemporalFactToolRequest request = map(arguments, TemporalFactToolRequest.class);
    return record(graphitiProvider.recordTriplet(
        request.sourceNode(),
        request.edgeName(),
        request.fact(),
        request.targetNode()
    ));
  }

  private MemoryKnowledgeContext record(MemoryKnowledgeContext context) {
    telemetryService.record(context);
    return context;
  }

  private MemoryKnowledgeQuery query(MemoryKnowledgeToolRequest request) {
    return new MemoryKnowledgeQuery(
        request.projectId(),
        request.repoPath(),
        request.queryText(),
        request.limit() == null ? 6 : request.limit(),
        request.metadata()
    );
  }

  private MemoryScope parseScope(String value) {
    return value == null || value.isBlank()
        ? MemoryScope.PROJECT
        : MemoryScope.valueOf(value.strip().toUpperCase(Locale.ROOT));
  }

  private MemoryKind parseKind(String value) {
    return value == null || value.isBlank()
        ? MemoryKind.REFLECTION
        : MemoryKind.valueOf(value.strip().toUpperCase(Locale.ROOT));
  }

  private Map<String, Object> memoryContextProperties() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("projectId", stringProperty("Project or repository memory scope."));
    properties.put("threadKey", stringProperty("Optional durable prompt-thread key."));
    properties.put("sessionId", stringProperty("Optional provider session id."));
    properties.put("requestedBy", stringProperty("Actor requesting memory."));
    properties.put("requestedFrom", stringProperty("Client or provider requesting memory."));
    properties.put("repoPath", stringProperty("Current local repository path for structural lookup."));
    properties.put("queryText", stringProperty("Task or question to hydrate."));
    properties.put("metadata", objectProperty("Additional identity and retrieval metadata."));
    return properties;
  }

  private Map<String, Object> memoryRecordProperties() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("projectId", stringProperty("Project or repository memory scope."));
    properties.put("threadKey", stringProperty("Optional thread identity for session-scoped memory."));
    properties.put("sessionId", stringProperty("Optional provider session id."));
    properties.put("requestedBy", stringProperty("Actor explicitly recording the memory."));
    properties.put("requestedFrom", stringProperty("Client or provider recording the memory."));
    properties.put("repoPath", stringProperty("Current repository path."));
    properties.put("scope", stringProperty("SESSION, PROJECT, or GLOBAL. Defaults to PROJECT."));
    properties.put("kind", stringProperty("Memory kind such as REFLECTION, PROJECT_STATE, CORRECTION, PREFERENCE, PROFILE, TASK, or EPISODIC."));
    properties.put("title", stringProperty("Stable concise memory title."));
    properties.put("summary", stringProperty("Distilled memory claim or conclusion; do not paste full chats."));
    properties.put("facts", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Optional distilled supporting facts."));
    properties.put("importance", integerProperty("Importance from 0 to 100; defaults to 75."));
    properties.put("sensitivity", stringProperty("Sensitivity label; defaults to internal."));
    properties.put("consentLevel", stringProperty("Write authority label; defaults to explicit."));
    properties.put("sourceReference", stringProperty("Evidence reference such as repo, PR, commit, issue, run, or session path."));
    properties.put("supersedesMemoryId", stringProperty("Optional memory id explicitly superseded by this record."));
    properties.put("metadata", objectProperty("Additional structured provenance metadata."));
    return properties;
  }

  private Map<String, Object> knowledgeQueryProperties() {
    return Map.of(
        "projectId", stringProperty("Project or repository memory scope."),
        "repoPath", stringProperty("Current local repository path."),
        "queryText", stringProperty("Question for the knowledge provider."),
        "limit", integerProperty("Maximum result count."),
        "metadata", objectProperty("Additional retrieval metadata.")
    );
  }

  private Map<String, Object> codeImpactProperties() {
    return Map.of(
        "repository", stringProperty("GitHub repository in owner/name form."),
        "pullRequestNumber", integerProperty("Pull request number."),
        "repoPath", stringProperty("Current local repository path containing graphify-out/graph.json.")
    );
  }

  private Map<String, Object> temporalFactProperties() {
    return Map.of(
        "sourceNode", stringProperty("Stable source entity name or Tavall reference."),
        "edgeName", stringProperty("Relationship name."),
        "fact", stringProperty("Verified relationship fact."),
        "targetNode", stringProperty("Stable target entity name or Tavall reference.")
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

  private Map<String, Object> objectProperty(String description) {
    return Map.of("type", "object", "description", description);
  }

  @FunctionalInterface
  private interface ToolCall {
    Object run(Map<String, Object> arguments);
  }
}
