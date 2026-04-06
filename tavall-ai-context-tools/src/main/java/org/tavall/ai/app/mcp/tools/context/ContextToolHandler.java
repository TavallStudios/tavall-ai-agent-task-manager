package org.tavall.ai.app.mcp.tools.context;

import org.tavall.ai.app.dashboard.DashboardSummaryService;
import org.tavall.ai.app.mcp.McpJsonSchemaFactory;
import org.tavall.ai.app.mcp.McpResultFactory;
import org.tavall.ai.app.mcp.McpToolPayloadMapper;
import org.tavall.ai.app.mcp.McpToolProvider;
import org.tavall.ai.app.mcp.McpToolSupport;
import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.model.orchestration.SharedTaskContext;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import org.tavall.ai.app.persistence.postgres.ValidationReportRepository;
import org.tavall.ai.app.service.PromptThreadService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ContextToolHandler extends McpToolSupport implements McpToolProvider {

  private final SharedTaskContextService sharedTaskContextService;
  private final ValidationReportRepository validationReportRepository;
  private final DashboardSummaryService dashboardSummaryService;
  private final PromptThreadService promptThreadService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public ContextToolHandler(
      SharedTaskContextService sharedTaskContextService,
      ValidationReportRepository validationReportRepository,
      DashboardSummaryService dashboardSummaryService,
      PromptThreadService promptThreadService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.sharedTaskContextService = sharedTaskContextService;
    this.validationReportRepository = validationReportRepository;
    this.dashboardSummaryService = dashboardSummaryService;
    this.promptThreadService = promptThreadService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "loadTaskContext",
            "Load aggregated task context.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            arguments -> new TaskContextResponse(sharedTaskContextService.loadTaskContext(map(arguments, ContextTaskIdRequest.class).taskId()))
        ),
        spec("loadArchitectureRules", "Load RULES.md content.", Map.of(), List.of(), arguments -> new TextPayloadResponse(readDoc("RULES.md"))),
        spec("loadUniversalGuidance", "Load UNIVERSAL.md content.", Map.of(), List.of(), arguments -> new TextPayloadResponse(readDoc("UNIVERSAL.md"))),
        spec("loadExamples", "Load EXAMPLES.md content.", Map.of(), List.of(), arguments -> new TextPayloadResponse(readDoc("EXAMPLES.md"))),
        spec(
            "loadValidationHistory",
            "Load validation history for a task.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            arguments -> new ValidationHistoryResponse(validationReportRepository.listReportsByTask(
                map(arguments, ContextTaskIdRequest.class).taskId()
            ))
        ),
        spec("loadDashboardSummary", "Load dashboard summary.", Map.of(), List.of(), arguments -> dashboardSummaryService.loadDashboardSummary()),
        spec(
            "loadChatState",
            "Load chat thread state.",
            Map.of("threadKey", stringProperty("Thread key.")),
            List.of("threadKey"),
            arguments -> new ChatStateResponse(promptThreadService.getDetail(map(arguments, ContextThreadKeyRequest.class).threadKey()))
        ),
        spec(
            "searchSemanticContext",
            "Search semantic context and return stored chunk payloads by query.",
            Map.of(
                "projectKey", stringProperty("Project key."),
                "queryText", stringProperty("Search query."),
                "limit", integerProperty("Result limit.")
            ),
            List.of("projectKey", "queryText"),
            arguments -> {
              SemanticContextRequest request = map(arguments, SemanticContextRequest.class);
              List<RetrievedSemanticContext> items = sharedTaskContextService.searchProjectRelatedContexts(
                  requireProjectKey(request.projectKey()),
                  request.queryText(),
                  normalizeLimit(request.limit())
              );
              return new SemanticContextResponse(items);
            }
        ),
        spec(
            "loadSiblingTaskSummaries",
            "Load sibling worker task summaries.",
            Map.of("taskId", stringProperty("Task id."), "workerTaskId", stringProperty("Worker task id.")),
            List.of("taskId", "workerTaskId"),
            arguments -> {
              SiblingSummaryRequest request = map(arguments, SiblingSummaryRequest.class);
              return new SiblingSummaryResponse(sharedTaskContextService.loadSiblingTaskSummaries(request.taskId(), request.workerTaskId()));
            }
        ),
        spec(
            "storeSharedTaskContext",
            "Store shared task context.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "contextKey", stringProperty("Context key."),
                "visibility", stringProperty("Visibility."),
                "summary", stringProperty("Summary."),
                "payload", Map.of("type", "object", "description", "Structured payload.")
            ),
            List.of("taskId", "contextKey", "visibility", "summary"),
            arguments -> {
              StoreSharedTaskContextRequest request = map(arguments, StoreSharedTaskContextRequest.class);
              SharedTaskContext context = sharedTaskContextService.storeSharedTaskContext(
                  request.taskId(),
                  request.workerTaskId(),
                  request.contextKey(),
                  request.visibility(),
                  request.summary(),
                  request.payload() == null ? Map.of() : request.payload()
              );
              return new SharedTaskContextResponse(context);
            }
        ),
        spec(
            "loadSharedTaskContext",
            "Load shared task context entries for a task.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            arguments -> new SharedTaskContextsResponse(
                (List<SharedTaskContext>) sharedTaskContextService.loadTaskContext(
                    map(arguments, ContextTaskIdRequest.class).taskId()
                ).get("contexts")
            )
        )
    );
  }

  private String readDoc(String fileName) {
    try {
      return Files.readString(Path.of(fileName), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      return "Failed to read " + fileName + ": " + exception.getMessage();
    }
  }

  private int normalizeLimit(Integer limit) {
    return limit == null ? 5 : limit;
  }

  private String requireProjectKey(String projectKey) {
    if (projectKey == null || projectKey.isBlank()) {
      throw new IllegalArgumentException("projectKey is required for semantic project search.");
    }
    return projectKey.strip();
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

