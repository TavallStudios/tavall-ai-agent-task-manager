package com.agenttaskmanager.app.mcp.tools.orchestration;

import com.agenttaskmanager.app.mcp.McpJsonSchemaFactory;
import com.agenttaskmanager.app.mcp.McpResultFactory;
import com.agenttaskmanager.app.mcp.McpToolPayloadMapper;
import com.agenttaskmanager.app.mcp.McpToolProvider;
import com.agenttaskmanager.app.mcp.McpToolSupport;
import com.agenttaskmanager.app.model.orchestration.CodexDelegationRunSnapshot;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.orchestration.CodexDelegationRunService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DelegationRunToolHandler extends McpToolSupport implements McpToolProvider {

  private final CodexDelegationRunService codexDelegationRunService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public DelegationRunToolHandler(
      CodexDelegationRunService codexDelegationRunService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.codexDelegationRunService = codexDelegationRunService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "startDelegationRun",
            "Start a canonical Codex delegation run.",
            Map.of(
                "taskId", stringProperty("Optional task id to associate with this run."),
                "projectKey", stringProperty("Project key."),
                "repoPath", stringProperty("Repository path."),
                "title", stringProperty("Delegation run title."),
                "metadata", objectProperty("Run metadata.")
            ),
            List.of("title"),
            arguments -> new DelegationRunResponse(startRun(arguments))
        ),
        spec(
            "appendDelegationRunEvent",
            "Append a delegation timeline event (spawn/wait/result/failure).",
            Map.of(
                "runId", stringProperty("Delegation run id."),
                "eventType", stringProperty("Event type."),
                "status", stringProperty("Lifecycle status."),
                "summary", stringProperty("Event summary."),
                "details", objectProperty("Event details.")
            ),
            List.of("runId", "eventType"),
            arguments -> new DelegationRunResponse(appendEvent(arguments))
        ),
        spec(
            "loadDelegationRun",
            "Load a delegation run with its timeline steps.",
            Map.of("runId", stringProperty("Delegation run id.")),
            List.of("runId"),
            arguments -> new DelegationRunResponse(loadRun(arguments))
        ),
        spec(
            "listDelegationRuns",
            "List delegation runs.",
            Map.of(
                "limit", integerProperty("Result limit."),
                "status", stringProperty("Optional status filter.")
            ),
            List.of(),
            arguments -> new DelegationRunListResponse(listRuns(arguments))
        ),
        spec(
            "completeDelegationRun",
            "Complete a delegation run and persist final status/summary.",
            Map.of(
                "runId", stringProperty("Delegation run id."),
                "status", stringProperty("Terminal status. Defaults to COMPLETED."),
                "summary", stringProperty("Final summary."),
                "details", objectProperty("Final details.")
            ),
            List.of("runId"),
            arguments -> new DelegationRunResponse(completeRun(arguments))
        )
    );
  }

  private CodexDelegationRunSnapshot startRun(Map<String, Object> arguments) {
    StartDelegationRunRequest request = map(arguments, StartDelegationRunRequest.class);
    return codexDelegationRunService.startRun(
        request.taskId(),
        request.projectKey(),
        request.repoPath(),
        request.title(),
        request.metadata()
    );
  }

  private CodexDelegationRunSnapshot appendEvent(Map<String, Object> arguments) {
    DelegationRunEventRequest request = map(arguments, DelegationRunEventRequest.class);
    return codexDelegationRunService.appendEvent(
        request.runId(),
        request.eventType(),
        parseStatus(request.status(), TaskLifecycleStatus.CHECKED_IN),
        request.summary(),
        request.details()
    );
  }

  private CodexDelegationRunSnapshot completeRun(Map<String, Object> arguments) {
    CompleteDelegationRunRequest request = map(arguments, CompleteDelegationRunRequest.class);
    return codexDelegationRunService.completeRun(
        request.runId(),
        parseStatus(request.status(), TaskLifecycleStatus.COMPLETED),
        request.summary(),
        request.details()
    );
  }

  private CodexDelegationRunSnapshot loadRun(Map<String, Object> arguments) {
    return codexDelegationRunService.loadRun(map(arguments, LoadDelegationRunRequest.class).runId());
  }

  private List<com.agenttaskmanager.app.model.orchestration.CodexDelegationRun> listRuns(Map<String, Object> arguments) {
    ListDelegationRunsRequest request = map(arguments, ListDelegationRunsRequest.class);
    int limit = request.limit() == null ? 20 : request.limit();
    return codexDelegationRunService.listRuns(limit, request.status());
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

  private TaskLifecycleStatus parseStatus(String status, TaskLifecycleStatus fallback) {
    if (status == null || status.isBlank()) {
      return fallback;
    }
    return TaskLifecycleStatus.valueOf(status.strip().toUpperCase());
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
