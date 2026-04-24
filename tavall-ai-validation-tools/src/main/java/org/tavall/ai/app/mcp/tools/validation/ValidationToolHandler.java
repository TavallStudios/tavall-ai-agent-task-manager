package org.tavall.ai.app.mcp.tools.validation;

import org.tavall.ai.app.mcp.McpJsonSchemaFactory;
import org.tavall.ai.app.mcp.McpResultFactory;
import org.tavall.ai.app.mcp.McpToolPayloadMapper;
import org.tavall.ai.app.mcp.McpToolProvider;
import org.tavall.ai.app.mcp.McpToolSupport;
import org.tavall.ai.app.model.validation.ValidationReport;
import org.tavall.ai.app.orchestration.CleanupReviewService;
import org.tavall.ai.app.validation.ValidationPipelineService;
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
        spec(
            "runArchUnitValidation",
            "Run ArchUnit rules.",
            Map.of("taskId", stringProperty("Task id."), "workerTaskId", stringProperty("Worker task id.")),
            List.of("taskId"),
            arguments -> validationPipelineService.runArchUnitValidation(
                map(arguments, ValidationRequest.class).taskId(),
                map(arguments, ValidationRequest.class).workerTaskId()
            )
        ),
        spec(
            "runSpoonValidation",
            "Run Spoon source-shape rules.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "repoPath", stringProperty("Repo path.")
            ),
            List.of("taskId", "repoPath"),
            arguments -> {
              SpoonValidationRequest request = map(arguments, SpoonValidationRequest.class);
              return validationPipelineService.runSpoonValidation(request.taskId(), request.workerTaskId(), Path.of(request.repoPath()));
            }
        ),
        spec(
            "runJavaLintValidation",
            "Run deterministic Java lint checks (Checkstyle, PMD, Error Prone).",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "repoPath", stringProperty("Repo path.")
            ),
            List.of("taskId", "repoPath"),
            arguments -> {
              JavaLintValidationRequest request = map(arguments, JavaLintValidationRequest.class);
              return validationPipelineService.runJavaLintValidation(request.taskId(), request.workerTaskId(), Path.of(request.repoPath()));
            }
        ),
        spec(
            "runIntegrationTests",
            "Run repository integration tests.",
            Map.of(
                "repoPath", stringProperty("Repo path."),
                "timeoutSeconds", integerProperty("Optional timeout override for this integration test run.")
            ),
            List.of("repoPath"),
            arguments -> {
              IntegrationRepoPathRequest request = map(arguments, IntegrationRepoPathRequest.class);
              return validationPipelineService.runIntegrationTests(Path.of(request.repoPath()), request.timeoutSeconds());
            }
        ),
        spec(
            "validatePatchScope",
            "Validate whether a diff stays within acceptable patch scope.",
            Map.of("diffBody", stringProperty("Diff body.")),
            List.of("diffBody"),
            arguments -> new PatchScopeResponse(validationPipelineService.validatePatchScope(
                map(arguments, PatchScopeRequest.class).diffBody()
            ))
        ),
        spec(
            "storeValidationReport",
            "Store a validation report.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "repoPath", stringProperty("Repo path.")
            ),
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

