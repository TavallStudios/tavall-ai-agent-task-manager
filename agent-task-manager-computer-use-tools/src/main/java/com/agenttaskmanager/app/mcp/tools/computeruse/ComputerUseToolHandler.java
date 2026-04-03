package com.agenttaskmanager.app.mcp.tools.computeruse;

import com.agenttaskmanager.app.computeruse.ComputerUseRunnerService;
import com.agenttaskmanager.app.mcp.McpJsonSchemaFactory;
import com.agenttaskmanager.app.mcp.McpResultFactory;
import com.agenttaskmanager.app.mcp.McpToolPayloadMapper;
import com.agenttaskmanager.app.mcp.McpToolProvider;
import com.agenttaskmanager.app.mcp.McpToolSupport;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ComputerUseToolHandler extends McpToolSupport implements McpToolProvider {

  private final ComputerUseRunnerService runnerService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public ComputerUseToolHandler(
      ComputerUseRunnerService runnerService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.runnerService = runnerService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        new SyncToolSpecification(
            tool(
                "registerComputerUseRunner",
                "Register or refresh an external computer-use runner.",
                Map.of(
                    "runnerId", stringProperty("Stable runner id."),
                    "displayName", stringProperty("Runner display name."),
                    "hostName", stringProperty("Runner host name."),
                    "baseUrl", stringProperty("Runner base URL."),
                    "launcherPath", stringProperty("Runner-local Hytale launcher path."),
                    "clientPath", stringProperty("Runner-local Hytale client path."),
                    "supportedCaptureModes", arrayProperty("Supported capture modes.", stringProperty("Capture mode.")),
                    "capabilities", objectProperty("Runner capabilities."),
                    "metadata", objectProperty("Extra runner metadata.")
                ),
                List.of("runnerId", "displayName", "hostName", "baseUrl")
            ),
            (exchange, request) -> resultFactory.toolResult(registerRunner(request.arguments()))
        ),
        new SyncToolSpecification(
            tool("listComputerUseRunners", "List registered computer-use runners.", Map.of(), List.of()),
            (exchange, request) -> resultFactory.toolResult(new ComputerUseRunnerListResponse(runnerService.listRunners()))
        ),
        new SyncToolSpecification(
            tool(
                "startComputerUseSession",
                "Start a computer-use session against a runner and scenario.",
                Map.of(
                    "runnerId", stringProperty("Runner id."),
                    "taskId", stringProperty("Optional task id."),
                    "workerTaskId", stringProperty("Optional worker task id."),
                    "scenarioId", stringProperty("Scenario id."),
                    "serverTarget", stringProperty("Server target."),
                    "chartId", stringProperty("Chart id."),
                    "expectedArtifacts", arrayProperty("Expected artifact kinds.", stringProperty("Artifact kind.")),
                    "passFailGates", arrayProperty("Expected gates.", stringProperty("Gate.")),
                    "artifactPolicy", objectProperty("Artifact policy."),
                    "metadata", objectProperty("Session metadata.")
                ),
                List.of("runnerId")
            ),
            (exchange, request) -> resultFactory.toolResult(startSession(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "launchComputerUseProcess",
                "Launch the configured launcher, client, or a custom process on the runner.",
                Map.of(
                    "sessionId", stringProperty("Session id."),
                    "launchTarget", stringProperty("launcher, client, or custom target."),
                    "fileName", stringProperty("Optional custom file path."),
                    "arguments", stringProperty("Arguments."),
                    "workingDirectory", stringProperty("Working directory."),
                    "waitForWindowMs", integerProperty("Window wait timeout."),
                    "windowTitleContains", stringProperty("Expected window title fragment.")
                ),
                List.of("sessionId", "launchTarget")
            ),
            (exchange, request) -> resultFactory.toolResult(launchProcess(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "captureComputerUseWindow",
                "Capture a current frame from the runner and optionally persist it as an artifact.",
                Map.of(
                    "sessionId", stringProperty("Session id."),
                    "titleContains", stringProperty("Window title fragment."),
                    "processName", stringProperty("Window process name."),
                    "storeArtifact", booleanProperty("Persist the capture as a session artifact."),
                    "artifactKind", stringProperty("Artifact kind."),
                    "summary", stringProperty("Artifact summary.")
                ),
                List.of("sessionId")
            ),
            (exchange, request) -> resultFactory.toolResult(captureWindow(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "sendComputerUseInput",
                "Send a keyboard and mouse batch through the runner.",
                Map.of(
                    "sessionId", stringProperty("Session id."),
                    "inputBatch", objectProperty("Keyboard and mouse input batch.")
                ),
                List.of("sessionId", "inputBatch")
            ),
            (exchange, request) -> resultFactory.toolResult(sendInput(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "waitForComputerUseVisionMatch",
                "Wait for a template or anchor to appear in the runner window capture.",
                Map.of(
                    "sessionId", stringProperty("Session id."),
                    "anchorKey", stringProperty("Known anchor key."),
                    "templatePath", stringProperty("Runner-local template path."),
                    "titleContains", stringProperty("Window title fragment."),
                    "processName", stringProperty("Window process name."),
                    "threshold", Map.of("type", "number", "description", "Match threshold."),
                    "timeoutMs", integerProperty("Timeout in milliseconds."),
                    "storeArtifact", booleanProperty("Persist the best attempt as an artifact.")
                ),
                List.of("sessionId")
            ),
            (exchange, request) -> resultFactory.toolResult(waitForVisionMatch(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "stopComputerUseSession",
                "Stop a computer-use session and release the runner lease.",
                Map.of("sessionId", stringProperty("Session id.")),
                List.of("sessionId")
            ),
            (exchange, request) -> resultFactory.toolResult(stopSession(request.arguments()))
        )
    );
  }

  private ComputerUseRunnerSummaryResponse registerRunner(Map<String, Object> arguments) {
    RegisterComputerUseRunnerRequest request = payloadMapper.map(arguments, RegisterComputerUseRunnerRequest.class);
    return new ComputerUseRunnerSummaryResponse(runnerService.registerRunner(request.toRegistration()));
  }

  private ComputerUseSessionSummaryResponse startSession(Map<String, Object> arguments) {
    StartComputerUseSessionRequest request = payloadMapper.map(arguments, StartComputerUseSessionRequest.class);
    return new ComputerUseSessionSummaryResponse(runnerService.startSession(request.toSessionRequest()));
  }

  private ComputerUseProcessLaunchResponse launchProcess(Map<String, Object> arguments) {
    LaunchComputerUseProcessRequest request = payloadMapper.map(arguments, LaunchComputerUseProcessRequest.class);
    return new ComputerUseProcessLaunchResponse(runnerService.launchProcess(request.toLaunchRequest()));
  }

  private ComputerUseCaptureResponse captureWindow(Map<String, Object> arguments) {
    CaptureComputerUseWindowRequest request = payloadMapper.map(arguments, CaptureComputerUseWindowRequest.class);
    return new ComputerUseCaptureResponse(runnerService.captureWindow(request.toCaptureRequest()));
  }

  private ComputerUseInputResponse sendInput(Map<String, Object> arguments) {
    SendComputerUseInputRequest request = payloadMapper.map(arguments, SendComputerUseInputRequest.class);
    return new ComputerUseInputResponse(runnerService.sendInput(request.sessionId(), request.inputBatch()));
  }

  private ComputerUseVisionMatchResponse waitForVisionMatch(Map<String, Object> arguments) {
    WaitForComputerUseVisionMatchRequest request = payloadMapper.map(arguments, WaitForComputerUseVisionMatchRequest.class);
    return new ComputerUseVisionMatchResponse(runnerService.waitForVisionMatch(request.toVisionRequest()));
  }

  private ComputerUseSessionSummaryResponse stopSession(Map<String, Object> arguments) {
    StopComputerUseSessionRequest request = payloadMapper.map(arguments, StopComputerUseSessionRequest.class);
    return new ComputerUseSessionSummaryResponse(runnerService.stopSession(request.sessionId()));
  }

  private Map<String, Object> stringProperty(String description) {
    return schemaFactory.stringProperty(description);
  }

  private Map<String, Object> booleanProperty(String description) {
    return schemaFactory.booleanProperty(description);
  }

  private Map<String, Object> integerProperty(String description) {
    return schemaFactory.integerProperty(description);
  }

  private Map<String, Object> arrayProperty(String description, Map<String, Object> itemSchema) {
    return schemaFactory.arrayProperty(description, itemSchema);
  }

  private Map<String, Object> objectProperty(String description) {
    return Map.of("type", "object", "description", description);
  }
}
