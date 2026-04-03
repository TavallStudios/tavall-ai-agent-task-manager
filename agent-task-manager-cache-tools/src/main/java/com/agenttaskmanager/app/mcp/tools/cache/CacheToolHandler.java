package com.agenttaskmanager.app.mcp.tools.cache;

import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.mcp.McpJsonSchemaFactory;
import com.agenttaskmanager.app.mcp.McpResultFactory;
import com.agenttaskmanager.app.mcp.McpToolPayloadMapper;
import com.agenttaskmanager.app.mcp.McpToolProvider;
import com.agenttaskmanager.app.mcp.McpToolSupport;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CacheToolHandler extends McpToolSupport implements McpToolProvider {

  private final SharedTaskContextService sharedTaskContextService;
  private final ValidationPipelineService validationPipelineService;
  private final DashboardSummaryService dashboardSummaryService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public CacheToolHandler(
      SharedTaskContextService sharedTaskContextService,
      ValidationPipelineService validationPipelineService,
      DashboardSummaryService dashboardSummaryService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.sharedTaskContextService = sharedTaskContextService;
    this.validationPipelineService = validationPipelineService;
    this.dashboardSummaryService = dashboardSummaryService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "cacheTaskContext",
            "Warm task context cache.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            arguments -> new CacheTaskContextResponse(sharedTaskContextService.loadTaskContext(map(arguments, CacheTaskIdRequest.class).taskId()))
        ),
        spec(
            "getCachedTaskContext",
            "Read cached task context.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            arguments -> new CacheTaskContextResponse(sharedTaskContextService.loadTaskContext(map(arguments, CacheTaskIdRequest.class).taskId()))
        ),
        spec(
            "cacheValidationSummary",
            "Warm validation cache for a stored report.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "repoPath", stringProperty("Repo path.")
            ),
            List.of("taskId", "repoPath"),
            this::cacheValidationSummary
        ),
        spec(
            "getCachedValidationSummary",
            "Read cached validation summary.",
            Map.of("taskId", stringProperty("Task id."), "workerTaskId", stringProperty("Worker task id.")),
            List.of("taskId"),
            arguments -> new CacheValidationSummaryResponse(validationPipelineService.getCachedValidationSummary(
                map(arguments, CacheValidationLookupRequest.class).taskId(),
                map(arguments, CacheValidationLookupRequest.class).workerTaskId()
            ))
        ),
        spec(
            "invalidateTaskCache",
            "Invalidate task context cache.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            this::invalidateTaskCache
        ),
        spec(
            "warmDashboardCache",
            "Warm dashboard cache.",
            Map.of(),
            List.of(),
            arguments -> dashboardSummaryService.warmDashboardCache()
        )
    );
  }

  private Object cacheValidationSummary(Map<String, Object> arguments) {
    CacheValidationRequest request = map(arguments, CacheValidationRequest.class);
    return validationPipelineService.runValidationPipeline(
        request.taskId(),
        request.workerTaskId(),
        Path.of(request.repoPath())
    );
  }

  private CacheStatusResponse invalidateTaskCache(Map<String, Object> arguments) {
    sharedTaskContextService.invalidateTaskCache(map(arguments, CacheTaskIdRequest.class).taskId());
    return new CacheStatusResponse("invalidated");
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

  @FunctionalInterface
  private interface ToolCall {
    Object run(Map<String, Object> arguments);
  }
}
