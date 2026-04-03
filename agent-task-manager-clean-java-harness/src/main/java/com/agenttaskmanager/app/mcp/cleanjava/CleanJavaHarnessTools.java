package com.agenttaskmanager.app.mcp.cleanjava;

import com.agenttaskmanager.app.harness.approval.HarnessApprovalGateResult;
import com.agenttaskmanager.app.harness.approval.HarnessApprovalService;
import com.agenttaskmanager.app.harness.cleanjava.CleanJavaDeterministicHarnessService;
import com.agenttaskmanager.app.harness.cleanjava.CleanJavaHarnessRunResult;
import com.agenttaskmanager.app.harness.cleanjava.CleanJavaTaskContext;
import com.agenttaskmanager.app.harness.cleanjava.CleanJavaTaskContextService;
import com.agenttaskmanager.app.harness.intake.HarnessTaskIntakeService;
import com.agenttaskmanager.app.harness.intake.ParentTaskRequest;
import com.agenttaskmanager.app.harness.intake.ParentTaskType;
import com.agenttaskmanager.app.harness.routing.HarnessRoutingService;
import com.agenttaskmanager.app.harness.state.HarnessStateService;
import com.agenttaskmanager.app.harness.tools.HarnessToolBundleRequest;
import com.agenttaskmanager.app.harness.tools.HarnessToolBundleService;
import com.agenttaskmanager.app.mcp.McpJsonSchemaFactory;
import com.agenttaskmanager.app.mcp.McpResultFactory;
import com.agenttaskmanager.app.mcp.McpToolPayloadMapper;
import com.agenttaskmanager.app.mcp.McpToolProvider;
import com.agenttaskmanager.app.mcp.McpToolSupport;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CleanJavaHarnessTools extends McpToolSupport implements McpToolProvider {

  private final HarnessApprovalService harnessApprovalService;
  private final CleanJavaDeterministicHarnessService cleanJavaDeterministicHarnessService;
  private final CleanJavaTaskContextService cleanJavaTaskContextService;
  private final HarnessRoutingService harnessRoutingService;
  private final HarnessStateService harnessStateService;
  private final HarnessTaskIntakeService harnessTaskIntakeService;
  private final HarnessToolBundleService harnessToolBundleService;
  private final ValidationPipelineService validationPipelineService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public CleanJavaHarnessTools(
      HarnessApprovalService harnessApprovalService,
      CleanJavaDeterministicHarnessService cleanJavaDeterministicHarnessService,
      CleanJavaTaskContextService cleanJavaTaskContextService,
      HarnessRoutingService harnessRoutingService,
      HarnessStateService harnessStateService,
      HarnessTaskIntakeService harnessTaskIntakeService,
      HarnessToolBundleService harnessToolBundleService,
      ValidationPipelineService validationPipelineService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.harnessApprovalService = harnessApprovalService;
    this.cleanJavaDeterministicHarnessService = cleanJavaDeterministicHarnessService;
    this.cleanJavaTaskContextService = cleanJavaTaskContextService;
    this.harnessRoutingService = harnessRoutingService;
    this.harnessStateService = harnessStateService;
    this.harnessTaskIntakeService = harnessTaskIntakeService;
    this.harnessToolBundleService = harnessToolBundleService;
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
            "intakeHarnessTask",
            "Accept parent work, route it into typed workers, and persist the shared harness state.",
            harnessTaskRequestProperties(),
            List.of("type", "title"),
            arguments -> new HarnessStateResponse(harnessTaskIntakeService.intakeTask(parentTaskRequest(arguments)))
        ),
        spec(
            "routeHarnessTask",
            "Route parent work into code, cleanup, computer-use, and retrieval workers without creating tasks.",
            harnessTaskRequestProperties(),
            List.of("type", "title"),
            arguments -> new HarnessRoutingResponse(harnessRoutingService.routeTask(parentTaskRequest(arguments)))
        ),
        spec(
            "loadHarnessState",
            "Load the shared task, agent, persistence, and dashboard models for a harness task.",
            Map.of("taskId", stringProperty("Task id.")),
            List.of("taskId"),
            arguments -> new HarnessStateResponse(harnessStateService.loadState(map(arguments, HarnessTaskIdRequest.class).taskId()))
        ),
        spec(
            "runHarnessToolBundle",
            "Broker repository, retrieval, and clean Java context through one harness call that fans out downstream MCP tools in parallel.",
            Map.of(
                "bundleName", stringProperty("Bundle name: repo-context, worker-context, or java-context."),
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "projectKey", stringProperty("Project key for semantic retrieval."),
                "repoPath", stringProperty("Repository path. Defaults to the current working directory when omitted."),
                "queryText", stringProperty("Search query for retrieval and ripgrep."),
                "limit", integerProperty("Result limit for search-oriented bundle calls.")
            ),
            List.of("bundleName"),
            arguments -> new HarnessToolBundleResponse(harnessToolBundleService.executeBundle(map(arguments, HarnessToolBundleRequest.class)))
        ),
        spec(
            "runHarnessApprovalGate",
            "Run the shared cleanup, validation, integration-test, and patch-scope gate for a worker task.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "repoPath", stringProperty("Repo path."),
                "diffArtifactId", stringProperty("Diff artifact id."),
                "workerExitCode", integerProperty("Worker exit code."),
                "requiresIntegrationTests", booleanProperty("Whether integration tests are required."),
                "integrationTimeoutSeconds", integerProperty("Optional timeout override for integration tests.")
            ),
            List.of("taskId", "workerTaskId", "repoPath"),
            arguments -> new HarnessApprovalResponse(runApprovalGate(arguments))
        ),
        spec(
            "loadCleanJavaTaskContext",
            "Build deterministic clean Java task context with rules, examples, semantic recall, package dependencies, and current validation state.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "projectKey", stringProperty("Project key for semantic retrieval."),
                "repoPath", stringProperty("Repo path."),
                "queryText", stringProperty("Optional retrieval query override.")
            ),
            List.of("repoPath"),
            arguments -> new CleanJavaTaskContextResponse(loadCleanJavaTaskContext(arguments))
        ),
        spec(
            "runCleanJavaHarness",
            "Run the deterministic clean Java harness: build task context, then Spoon source-shape feedback, then ArchUnit architecture and cycle feedback.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "projectKey", stringProperty("Project key for semantic retrieval."),
                "repoPath", stringProperty("Repo path."),
                "queryText", stringProperty("Optional retrieval query override.")
            ),
            List.of("taskId", "repoPath"),
            arguments -> new CleanJavaHarnessRunResponse(runCleanJavaHarness(arguments))
        ),
        spec(
            "runJavaIntegrationHarness",
            "Run the deterministic Java integration-test harness.",
            Map.of(
                "repoPath", stringProperty("Repo path."),
                "timeoutSeconds", integerProperty("Optional timeout override for this integration harness run.")
            ),
            List.of("repoPath"),
            arguments -> {
              CleanJavaHarnessRepoPathRequest request = map(arguments, CleanJavaHarnessRepoPathRequest.class);
              return validationPipelineService.runIntegrationTests(Path.of(request.repoPath()), request.timeoutSeconds());
            }
        )
    );
  }

  private ParentTaskRequest parentTaskRequest(Map<String, Object> arguments) {
    HarnessTaskRequest request = map(arguments, HarnessTaskRequest.class);
    return new ParentTaskRequest(
        request.taskId(),
        ParentTaskType.fromValue(request.type()),
        request.title(),
        request.description(),
        request.repoRef(),
        request.priority(),
        request.requestedBy(),
        request.requiresCleanupReview(),
        request.requiresIntegrationTests(),
        request.multiAgentEnabled(),
        request.requestedWorkerTypes(),
        request.changedFiles(),
        request.gitBase(),
        request.gitHead(),
        request.codebaseInput(),
        request.storedContextInput(),
        request.ruleInput(),
        request.liveDebugInput(),
        request.metadata()
    );
  }

  private HarnessApprovalGateResult runApprovalGate(Map<String, Object> arguments) {
    HarnessApprovalRequest request = map(arguments, HarnessApprovalRequest.class);
    return harnessApprovalService.runApprovalGate(
        request.taskId(),
        request.workerTaskId(),
        Path.of(request.repoPath()),
        request.diffArtifactId(),
        request.workerExitCode(),
        request.requiresIntegrationTests(),
        request.integrationTimeoutSeconds()
    );
  }

  private CleanJavaTaskContext loadCleanJavaTaskContext(Map<String, Object> arguments) {
    CleanJavaHarnessRequest request = map(arguments, CleanJavaHarnessRequest.class);
    return cleanJavaTaskContextService.buildContext(
        request.taskId(),
        request.workerTaskId(),
        request.projectKey(),
        Path.of(request.repoPath()),
        request.queryText()
    );
  }

  private CleanJavaHarnessRunResult runCleanJavaHarness(Map<String, Object> arguments) {
    CleanJavaHarnessRequest request = map(arguments, CleanJavaHarnessRequest.class);
    return cleanJavaDeterministicHarnessService.run(
        request.taskId(),
        request.workerTaskId(),
        request.projectKey(),
        Path.of(request.repoPath()),
        request.queryText()
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

  private Map<String, Object> integerProperty(String description) {
    return schemaFactory.integerProperty(description);
  }

  private Map<String, Object> booleanProperty(String description) {
    return schemaFactory.booleanProperty(description);
  }

  private Map<String, Object> arrayProperty(String description) {
    return schemaFactory.arrayProperty(description, schemaFactory.stringProperty("String item."));
  }

  private Map<String, Object> objectProperty(String description) {
    return Map.of("type", "object", "description", description);
  }

  private Map<String, Object> harnessTaskRequestProperties() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("taskId", stringProperty("Optional external task id."));
    properties.put("type", stringProperty("Parent task type."));
    properties.put("title", stringProperty("Task title."));
    properties.put("description", stringProperty("Task description."));
    properties.put("repoRef", stringProperty("Repository path or current-worktree."));
    properties.put("priority", stringProperty("Priority label."));
    properties.put("requestedBy", stringProperty("Requester."));
    properties.put("requiresCleanupReview", booleanProperty("Whether cleanup review is required."));
    properties.put("requiresIntegrationTests", booleanProperty("Whether integration tests are required."));
    properties.put("multiAgentEnabled", booleanProperty("Whether multi-agent fan-out is enabled."));
    properties.put("requestedWorkerTypes", arrayProperty("Explicit worker types."));
    properties.put("changedFiles", arrayProperty("Changed files."));
    properties.put("gitBase", stringProperty("Git base revision."));
    properties.put("gitHead", stringProperty("Git head revision."));
    properties.put("codebaseInput", objectProperty("Codebase and diff input."));
    properties.put("storedContextInput", objectProperty("Stored run and task context."));
    properties.put("ruleInput", objectProperty("Architecture and rule input."));
    properties.put("liveDebugInput", objectProperty("Live debug input."));
    properties.put("metadata", objectProperty("Additional metadata."));
    return properties;
  }

  @FunctionalInterface
  private interface ToolCall {
    Object run(Map<String, Object> arguments);
  }
}
