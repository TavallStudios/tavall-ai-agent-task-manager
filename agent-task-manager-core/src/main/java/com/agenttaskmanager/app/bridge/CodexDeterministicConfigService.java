package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.config.CodexExecutionProperties;
import com.agenttaskmanager.app.mcp.McpServerProcessConfiguration;
import com.agenttaskmanager.app.mcp.McpServerProcessConfigurationService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CodexDeterministicConfigService {

  private final CodexExecutionProperties properties;
  private final McpServerProcessConfigurationService processConfigurationService;

  public CodexDeterministicConfigService(
      CodexExecutionProperties properties,
      McpServerProcessConfigurationService processConfigurationService
  ) {
    this.properties = properties;
    this.processConfigurationService = processConfigurationService;
  }

  public void appendDeterministicArguments(List<String> command, String projectKey) {
    appendConfig(command, "model_reasoning_effort", tomlString(properties.getReasoningEffort()));
    for (String serverName : configuredServerNames()) {
      McpServerProcessConfiguration configuration = processConfigurationService.resolve(serverName, projectKey);
      appendConfig(
          command,
          "mcp_servers." + serverName + ".command",
          tomlString(configuration.command())
      );
      if (!configuration.args().isEmpty()) {
        appendConfig(
            command,
            "mcp_servers." + serverName + ".args",
            tomlArray(configuration.args())
        );
      }
      for (var entry : configuration.env().entrySet()) {
        appendConfig(
            command,
            "mcp_servers." + serverName + ".env." + entry.getKey(),
            tomlString(entry.getValue())
        );
      }
    }
    for (String directory : properties.getAddDirectories()) {
      if (directory != null && !directory.isBlank()) {
        command.add("--add-dir");
        command.add(directory);
      }
    }
  }

  private List<String> configuredServerNames() {
    String centralServer = properties.getDownstreamCentralServer();
    if (centralServer != null && !centralServer.isBlank()) {
      return List.of(centralServer.strip());
    }
    List<String> requiredServers = new ArrayList<>();
    for (String serverName : properties.getRequiredMcpServers()) {
      if (serverName != null && !serverName.isBlank()) {
        requiredServers.add(serverName.strip());
      }
    }
    return requiredServers;
  }

  private static void appendConfig(List<String> command, String key, String value) {
    command.add("-c");
    command.add(key + "=" + value);
  }

  private static String tomlArray(List<String> values) {
    return values.stream()
        .map(CodexDeterministicConfigService::tomlString)
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static String tomlString(String value) {
    String normalized = value == null ? "" : value;
    return '"' + normalized.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
  }
}
