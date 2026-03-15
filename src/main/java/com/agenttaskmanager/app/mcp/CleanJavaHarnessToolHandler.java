package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.model.validation.ValidationReport;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CleanJavaHarnessToolHandler extends McpToolSupport implements McpToolProvider {

  private final ValidationPipelineService validationPipelineService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public CleanJavaHarnessToolHandler(
      ValidationPipelineService validationPipelineService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.validationPipelineService = validationPipelineService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<String> serverGroups() {
    return List.of("clean-java-harness");
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "runCleanJavaHarness",
            "Run the deterministic clean Java harness across ArchUnit and Spoon.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "repoPath", stringProperty("Repo path.")
            ),
            List.of("taskId", "repoPath"),
            arguments -> {
              CleanJavaHarnessRequest request = map(arguments, CleanJavaHarnessRequest.class);
              ValidationReport report = validationPipelineService.runValidationPipeline(
                  request.taskId(),
                  request.workerTaskId(),
                  Path.of(request.repoPath())
              );
              return validationPipelineService.storeValidationReport(request.taskId(), request.workerTaskId(), report);
            }
        ),
        spec(
            "runJavaIntegrationHarness",
            "Run the deterministic Java integration-test harness.",
            Map.of("repoPath", stringProperty("Repo path.")),
            List.of("repoPath"),
            arguments -> validationPipelineService.runIntegrationTests(Path.of(map(arguments, CleanJavaHarnessRepoPathRequest.class).repoPath()))
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

record CleanJavaHarnessRequest(String taskId, String workerTaskId, String repoPath) {
}

record CleanJavaHarnessRepoPathRequest(String repoPath) {
}
