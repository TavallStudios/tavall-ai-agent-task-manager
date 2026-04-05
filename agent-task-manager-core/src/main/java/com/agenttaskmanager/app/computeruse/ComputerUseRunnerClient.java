package com.agenttaskmanager.app.computeruse;

import com.agenttaskmanager.app.config.ComputerUseProperties;
import com.agenttaskmanager.app.model.computeruse.ComputerUseCaptureRequest;
import com.agenttaskmanager.app.model.computeruse.ComputerUseInputBatch;
import com.agenttaskmanager.app.model.computeruse.ComputerUseProcessLaunchRequest;
import com.agenttaskmanager.app.model.computeruse.ComputerUseRunnerSummary;
import com.agenttaskmanager.app.model.computeruse.ComputerUseVisionWaitRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ComputerUseRunnerClient {

  private static final String LEGACY_COMMAND_PATH = "/request";
  private static final String OWNER_HEADER = "X-AgentTaskManager-Runner-Owner";
  private static final String DEFAULT_OWNER = "agent-task-manager-control-plane";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final ComputerUseProperties properties;
  private final Map<String, RunnerEndpointResolution> endpointByRunnerId = new ConcurrentHashMap<>();

  public ComputerUseRunnerClient(HttpClient httpClient, ObjectMapper objectMapper, ComputerUseProperties properties) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  public void ping(ComputerUseRunnerSummary runner) {
    ensureRunnerEndpoint(runner);
    call(runner, "ping", Map.of());
  }

  public Map<String, Object> launchProcess(ComputerUseRunnerSummary runner, ComputerUseProcessLaunchRequest request) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("fileName", request.fileName());
    parameters.put("arguments", emptyIfNull(request.arguments()));
    parameters.put("workingDirectory", emptyIfNull(request.workingDirectory()));
    parameters.put("waitForInputIdle", true);
    parameters.put("waitForWindowMs", request.waitForWindowMs());
    parameters.put("windowTitleContains", emptyIfNull(request.windowTitleContains()));
    return call(runner, "launch_process", parameters);
  }

  public Map<String, Object> captureWindow(ComputerUseRunnerSummary runner, ComputerUseCaptureRequest request) {
    return call(runner, "capture_stream_frame", Map.of(
        "window", windowTarget(request.titleContains(), request.processName()),
        "allowScreenCopyFallback", true,
        "includeBase64", true
    ));
  }

  public Map<String, Object> sendInput(ComputerUseRunnerSummary runner, ComputerUseInputBatch batch) {
    Map<String, Object> latest = Map.of();
    if (batch.keyboardActions() != null && !batch.keyboardActions().isEmpty()) {
      latest = call(runner, "send_key_batch", Map.of(
          "activateWindow", batch.activateWindow(),
          "events", batch.keyboardActions()
      ));
    }
    if (batch.mouseActions() != null && !batch.mouseActions().isEmpty()) {
      latest = call(runner, "send_mouse_batch", Map.of(
          "activateWindow", batch.activateWindow(),
          "events", batch.mouseActions()
      ));
    }
    return latest;
  }

  public Map<String, Object> matchTemplate(ComputerUseRunnerSummary runner, ComputerUseVisionWaitRequest request) {
    return call(runner, "match_template", Map.of(
        "window", windowTarget(request.titleContains(), request.processName()),
        "templatePath", request.templatePath(),
        "threshold", request.threshold(),
        "includeBase64", request.storeArtifact()
    ));
  }

  private Map<String, Object> call(ComputerUseRunnerSummary runner, String command, Map<String, Object> parameters) {
    RunnerEndpointResolution resolution = ensureRunnerEndpoint(runner);
    return call(runner, command, parameters, resolution.commandPath(), true);
  }

  private Map<String, Object> call(
      ComputerUseRunnerSummary runner,
      String command,
      Map<String, Object> parameters,
      String commandPath,
      boolean allowCompatibilityFallback
  ) {
    try {
      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(commandUri(runner, commandPath))
          .timeout(Duration.ofSeconds(20))
          .header("Content-Type", "application/json")
          .header(OWNER_HEADER, DEFAULT_OWNER + ":" + runner.runnerId())
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
              Map.of("id", command, "command", command, "parameters", parameters)
          )));
      if (properties.getRunnerAuthToken() != null && !properties.getRunnerAuthToken().isBlank()) {
        requestBuilder.header("Authorization", "Bearer " + properties.getRunnerAuthToken().strip());
      }
      HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        if (allowCompatibilityFallback
            && response.statusCode() == 404
            && !LEGACY_COMMAND_PATH.equals(commandPath)) {
          endpointByRunnerId.put(runner.runnerId(), new RunnerEndpointResolution(LEGACY_COMMAND_PATH, false, true));
          return call(runner, command, parameters, LEGACY_COMMAND_PATH, false);
        }
        throw new IllegalStateException("Runner call failed with HTTP " + response.statusCode());
      }
      Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
      });
      if (!Boolean.TRUE.equals(payload.get("ok"))) {
        Map<String, Object> error = payload.get("error") instanceof Map<?, ?> map ? castMap(map) : Map.of();
        throw new IllegalStateException(String.valueOf(error.getOrDefault("message", "Runner command failed.")));
      }
      Object result = payload.get("result");
      return result instanceof Map<?, ?> map ? castMap(map) : Map.of("value", result);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to call the computer-use runner.", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while calling the computer-use runner.", exception);
    }
  }

  private RunnerEndpointResolution ensureRunnerEndpoint(ComputerUseRunnerSummary runner) {
    return endpointByRunnerId.computeIfAbsent(runner.runnerId(), ignored -> resolveRunnerEndpoint(runner));
  }

  private RunnerEndpointResolution resolveRunnerEndpoint(ComputerUseRunnerSummary runner) {
    String fallbackCommandPath = normalizePath(properties.getRunnerCommandPath());
    String capabilityPath = normalizePath(properties.getRunnerCapabilitiesPath());
    try {
      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(commandUri(runner, capabilityPath))
          .timeout(Duration.ofSeconds(10))
          .GET();
      if (properties.getRunnerAuthToken() != null && !properties.getRunnerAuthToken().isBlank()) {
        requestBuilder.header("Authorization", "Bearer " + properties.getRunnerAuthToken().strip());
      }
      HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 400) {
        Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
        });
        Map<String, Object> result = payload.get("result") instanceof Map<?, ?> map ? castMap(map) : Map.of();
        String commandPath = readCommandPathFromCapabilities(result);
        return new RunnerEndpointResolution(commandPath == null ? fallbackCommandPath : normalizePath(commandPath), true, false);
      }
    } catch (IOException exception) {
      // Fall back to configured command path when capabilities are unavailable.
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while resolving runner capabilities.", exception);
    }
    return new RunnerEndpointResolution(fallbackCommandPath, false, false);
  }

  private String readCommandPathFromCapabilities(Map<String, Object> result) {
    if (!(result.get("endpoints") instanceof Map<?, ?> endpoints)) {
      return null;
    }
    Object commandPath = endpoints.get("command");
    if (commandPath == null) {
      return null;
    }
    String normalized = String.valueOf(commandPath).trim();
    return normalized.isBlank() ? null : normalized;
  }

  private URI commandUri(ComputerUseRunnerSummary runner, String commandPath) {
    String normalizedBase = runner.baseUrl().endsWith("/")
        ? runner.baseUrl().substring(0, runner.baseUrl().length() - 1)
        : runner.baseUrl();
    return URI.create(normalizedBase + normalizePath(commandPath));
  }

  private String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return LEGACY_COMMAND_PATH;
    }
    String trimmed = path.trim();
    return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
  }

  private Map<String, Object> windowTarget(String titleContains, String processName) {
    Map<String, Object> target = new LinkedHashMap<>();
    if (titleContains != null && !titleContains.isBlank()) {
      target.put("titleContains", titleContains);
    }
    if (processName != null && !processName.isBlank()) {
      target.put("processName", processName);
    }
    return target;
  }

  private String emptyIfNull(String value) {
    return value == null ? "" : value;
  }

  private Map<String, Object> castMap(Map<?, ?> source) {
    Map<String, Object> target = new LinkedHashMap<>();
    source.forEach((key, value) -> target.put(String.valueOf(key), value));
    return target;
  }

  private record RunnerEndpointResolution(String commandPath, boolean capabilitiesAvailable, boolean compatibilityMode) {
  }
}
