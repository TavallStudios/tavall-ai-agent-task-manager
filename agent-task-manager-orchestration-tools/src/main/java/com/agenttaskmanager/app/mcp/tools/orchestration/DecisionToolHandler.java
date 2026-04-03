package com.agenttaskmanager.app.mcp.tools.orchestration;

import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.mcp.McpJsonSchemaFactory;
import com.agenttaskmanager.app.mcp.McpResultFactory;
import com.agenttaskmanager.app.mcp.McpToolPayloadMapper;
import com.agenttaskmanager.app.mcp.McpToolProvider;
import com.agenttaskmanager.app.mcp.McpToolSupport;
import com.agenttaskmanager.app.model.orchestration.AutonomousCycleReport;
import com.agenttaskmanager.app.model.orchestration.OverseerDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.PatchDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.orchestration.AutonomousCycleService;
import com.agenttaskmanager.app.orchestration.OverseerOrchestrationService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DecisionToolHandler extends McpToolSupport implements McpToolProvider {

  private final AutonomousCycleService autonomousCycleService;
  private final OverseerOrchestrationService overseerOrchestrationService;
  private final DashboardSummaryService dashboardSummaryService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public DecisionToolHandler(
      AutonomousCycleService autonomousCycleService,
      OverseerOrchestrationService overseerOrchestrationService,
      DashboardSummaryService dashboardSummaryService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.autonomousCycleService = autonomousCycleService;
    this.overseerOrchestrationService = overseerOrchestrationService;
    this.dashboardSummaryService = dashboardSummaryService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "mergeWorkerOutputs",
            "Merge worker output summaries.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            arguments -> new MergeResultResponse(overseerOrchestrationService.mergeWorkerOutputs(
                map(arguments, PatchDecisionRequest.class).taskId()
            ))
        ),
        spec("approvePatch", "Approve a patch after validation and cleanup review.", patchDecisionProperties(), List.of("taskId"),
            arguments -> new PatchDecisionResponse(decidePatch(arguments, true))),
        spec("rejectPatch", "Reject a patch and require rework.", patchDecisionProperties(), List.of("taskId"),
            arguments -> new PatchDecisionResponse(decidePatch(arguments, false))),
        spec(
            "storeOverseerDecision",
            "Store an overseer decision record.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "summary", stringProperty("Summary."),
                "status", stringProperty("Status."),
                "decisionType", stringProperty("Decision type."),
                "workerTaskId", stringProperty("Worker task id.")
            ),
            List.of("taskId", "summary", "status", "decisionType"),
            arguments -> new OverseerDecisionResponse(storeOverseerDecision(arguments))
        ),
        spec(
            "storeRunSummary",
            "Store the final run summary for a batch.",
            Map.of("taskId", stringProperty("Task id."), "summary", stringProperty("Summary.")),
            List.of("taskId", "summary"),
            arguments -> {
              RunSummaryRequest request = map(arguments, RunSummaryRequest.class);
              return new OverseerDecisionResponse(overseerOrchestrationService.storeRunSummary(request.taskId(), request.summary()));
            }
        ),
        spec(
            "runAutonomousCycle",
            "Run one autonomous orchestration cycle across open task batches.",
            Map.of("repoPath", stringProperty("Fallback repository path.")),
            List.of(),
            arguments -> new AutonomousCycleResponse(runAutonomousCycle(arguments))
        ),
        spec(
            "publishDashboardUpdate",
            "Warm the dashboard cache and publish a fresh summary.",
            Map.of(),
            List.of(),
            arguments -> dashboardSummaryService.warmDashboardCache()
        )
    );
  }

  private PatchDecisionRecord decidePatch(Map<String, Object> arguments, boolean approved) {
    PatchDecisionRequest request = map(arguments, PatchDecisionRequest.class);
    return overseerOrchestrationService.decidePatch(
        request.taskId(),
        request.workerTaskId(),
        request.validationReportId(),
        request.cleanupReviewId(),
        request.diffArtifactId(),
        approved
    );
  }

  private OverseerDecisionRecord storeOverseerDecision(Map<String, Object> arguments) {
    OverseerDecisionRequest request = map(arguments, OverseerDecisionRequest.class);
    return overseerOrchestrationService.storeOverseerDecision(
        request.taskId(),
        request.workerTaskId(),
        request.decisionType(),
        TaskLifecycleStatus.valueOf(request.status()),
        request.summary(),
        Map.of()
    );
  }

  private AutonomousCycleReport runAutonomousCycle(Map<String, Object> arguments) {
    AutonomousRepoPathRequest request = map(arguments, AutonomousRepoPathRequest.class);
    Path repoPath = request.repoPath() == null || request.repoPath().isBlank()
        ? Path.of(".").toAbsolutePath()
        : Path.of(request.repoPath()).toAbsolutePath();
    return autonomousCycleService.runCycle(repoPath);
  }

  private Map<String, Object> patchDecisionProperties() {
    return Map.of(
        "taskId", stringProperty("Task id."),
        "workerTaskId", stringProperty("Worker task id."),
        "validationReportId", stringProperty("Validation report id."),
        "cleanupReviewId", stringProperty("Cleanup review id."),
        "diffArtifactId", stringProperty("Diff artifact id.")
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

  @FunctionalInterface
  private interface ToolCall {
    Object run(Map<String, Object> arguments);
  }
}
