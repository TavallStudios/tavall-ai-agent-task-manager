package org.tavall.ai.app.mcp.tools.orchestration;

import org.tavall.ai.app.config.OrchestrationProperties;
import org.tavall.ai.app.dashboard.DashboardSummaryService;
import org.tavall.ai.app.mcp.McpJsonSchemaFactory;
import org.tavall.ai.app.mcp.McpResultFactory;
import org.tavall.ai.app.mcp.McpToolPayloadMapper;
import org.tavall.ai.app.mcp.McpToolProvider;
import org.tavall.ai.app.mcp.McpToolSupport;
import org.tavall.ai.app.model.orchestration.AutonomousCycleReport;
import org.tavall.ai.app.model.orchestration.CodexDelegationRunSnapshot;
import org.tavall.ai.app.model.orchestration.OverseerDecisionRecord;
import org.tavall.ai.app.model.orchestration.PatchDecisionRecord;
import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.orchestration.CodexDelegationRunService;
import org.tavall.ai.app.orchestration.AutonomousCycleService;
import org.tavall.ai.app.orchestration.OverseerOrchestrationService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DecisionToolHandler extends McpToolSupport implements McpToolProvider {

  private final AutonomousCycleService autonomousCycleService;
  private final CodexDelegationRunService codexDelegationRunService;
  private final OverseerOrchestrationService overseerOrchestrationService;
  private final OrchestrationProperties orchestrationProperties;
  private final DashboardSummaryService dashboardSummaryService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public DecisionToolHandler(
      AutonomousCycleService autonomousCycleService,
      CodexDelegationRunService codexDelegationRunService,
      OverseerOrchestrationService overseerOrchestrationService,
      OrchestrationProperties orchestrationProperties,
      DashboardSummaryService dashboardSummaryService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.autonomousCycleService = autonomousCycleService;
    this.codexDelegationRunService = codexDelegationRunService;
    this.overseerOrchestrationService = overseerOrchestrationService;
    this.orchestrationProperties = orchestrationProperties;
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
            arguments -> mergeWorkerOutputs(arguments)
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
            arguments -> runAutonomousCycleResponse(arguments)
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
    if (!orchestrationProperties.isLegacyAutonomyEnabled()) {
      return new AutonomousCycleReport(
          0,
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          dashboardSummaryService.warmDashboardCache(),
          OffsetDateTime.now()
      );
    }
    AutonomousRepoPathRequest request = map(arguments, AutonomousRepoPathRequest.class);
    Path repoPath = request.repoPath() == null || request.repoPath().isBlank()
        ? Path.of(".").toAbsolutePath()
        : Path.of(request.repoPath()).toAbsolutePath();
    return autonomousCycleService.runCycle(repoPath);
  }

  private MergeResultResponse mergeWorkerOutputs(Map<String, Object> arguments) {
    String taskId = map(arguments, PatchDecisionRequest.class).taskId();
    Optional<CodexDelegationRunSnapshot> snapshot = codexDelegationRunService.appendEventByTaskId(
        taskId,
        "compat.mergeWorkerOutputs",
        TaskLifecycleStatus.CHECKED_IN,
        "Legacy mergeWorkerOutputs mapped to canonical delegation run.",
        Map.of("taskId", taskId)
    );
    return new MergeResultResponse(
        overseerOrchestrationService.mergeWorkerOutputs(taskId),
        compatibility("mergeWorkerOutputs", snapshot.map(run -> run.run().runId()).orElse(null))
    );
  }

  private AutonomousCycleResponse runAutonomousCycleResponse(Map<String, Object> arguments) {
    AutonomousCycleReport report = runAutonomousCycle(arguments);
    String note = orchestrationProperties.isLegacyAutonomyEnabled()
        ? "Legacy autonomy loop is enabled by rollback flag."
        : "Legacy autonomy loop is disabled; use delegation run tools for canonical orchestration.";
    return new AutonomousCycleResponse(
        report,
        Map.of(
            "deprecated", true,
            "legacyTool", "runAutonomousCycle",
            "legacyAutonomyEnabled", orchestrationProperties.isLegacyAutonomyEnabled(),
            "canonicalTools", List.of("startDelegationRun", "appendDelegationRunEvent", "loadDelegationRun", "listDelegationRuns", "completeDelegationRun"),
            "notes", note
        )
    );
  }

  private Map<String, Object> compatibility(String legacyTool, String runId) {
    return Map.of(
        "deprecated", true,
        "legacyTool", legacyTool,
        "canonicalTools", List.of("startDelegationRun", "appendDelegationRunEvent", "loadDelegationRun", "listDelegationRuns", "completeDelegationRun"),
        "delegationRunId", runId == null ? "" : runId,
        "notes", "Legacy orchestration tool mapped through compatibility adapter to codex delegation run."
    );
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

