package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.orchestration.CleanupReviewService;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewResult;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import com.agenttaskmanager.app.model.validation.ValidationReport;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ValidationToolHandler extends McpToolSupport implements McpToolProvider {

  private final ValidationPipelineService validationPipelineService;
  private final CleanupReviewService cleanupReviewService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public ValidationToolHandler(
      ValidationPipelineService validationPipelineService,
      CleanupReviewService cleanupReviewService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.validationPipelineService = validationPipelineService;
    this.cleanupReviewService = cleanupReviewService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec("runArchUnitValidation", "Run ArchUnit rules.", Map.of("taskId", stringProperty("Task id."), "workerTaskId", stringProperty("Worker task id.")), List.of("taskId"),
            arguments -> validationPipelineService.runArchUnitValidation(map(arguments, ValidationRequest.class).taskId(), map(arguments, ValidationRequest.class).workerTaskId())),
        spec(
            "runSpoonValidation",
            "Run Spoon source-shape rules.",
            Map.of("taskId", stringProperty("Task id."), "workerTaskId", stringProperty("Worker task id."), "repoPath", stringProperty("Repo path.")),
            List.of("taskId", "repoPath"),
            arguments -> {
              SpoonValidationRequest request = map(arguments, SpoonValidationRequest.class);
              return validationPipelineService.runSpoonValidation(request.taskId(), request.workerTaskId(), Path.of(request.repoPath()));
            }
        ),
        spec(
            "runIntegrationTests",
            "Run repository integration tests.",
            Map.of("repoPath", stringProperty("Repo path.")),
            List.of("repoPath"),
            arguments -> validationPipelineService.runIntegrationTests(Path.of(map(arguments, RepoPathRequest.class).repoPath()))
        ),
        spec(
            "validatePatchScope",
            "Validate whether a diff stays within acceptable patch scope.",
            Map.of("diffBody", stringProperty("Diff body.")),
            List.of("diffBody"),
            arguments -> new PatchScopeResponse(validationPipelineService.validatePatchScope(map(arguments, PatchScopeRequest.class).diffBody()))
        ),
        spec(
            "storeValidationReport",
            "Store a validation report.",
            Map.of("taskId", stringProperty("Task id."), "workerTaskId", stringProperty("Worker task id."), "repoPath", stringProperty("Repo path.")),
            List.of("taskId", "repoPath"),
            arguments -> {
              SpoonValidationRequest request = map(arguments, SpoonValidationRequest.class);
              ValidationReport report = validationPipelineService.runValidationPipeline(
                  request.taskId(),
                  request.workerTaskId(),
                  Path.of(request.repoPath())
              );
              return validationPipelineService.storeValidationReport(request.taskId(), request.workerTaskId(), report);
            }
        ),
        spec(
            "runCleanupDiffReview",
            "Run cleanup diff review for a cleanup review id.",
            Map.of("cleanupReviewId", stringProperty("Cleanup review id.")),
            List.of("cleanupReviewId"),
            arguments -> cleanupReviewService.runCleanupDiffReview(map(arguments, CleanupReviewIdRequest.class).cleanupReviewId())
        )
    );
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

  @FunctionalInterface
  private interface ToolCall {
    Object run(Map<String, Object> arguments);
  }
}

record ValidationRequest(String taskId, String workerTaskId) {
}

record SpoonValidationRequest(String taskId, String workerTaskId, String repoPath) {
}

record RepoPathRequest(String repoPath) {
}

record PatchScopeRequest(String diffBody) {
}

record CleanupReviewIdRequest(String cleanupReviewId) {
}

record PatchScopeResponse(boolean allowed) {
}
