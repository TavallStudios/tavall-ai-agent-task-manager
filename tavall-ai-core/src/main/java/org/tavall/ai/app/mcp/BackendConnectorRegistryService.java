package org.tavall.ai.app.mcp;

import org.tavall.ai.app.config.McpServerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BackendConnectorRegistryService {

  private final McpServerProperties properties;
  private final ObjectMapper objectMapper;

  public BackendConnectorRegistryService(McpServerProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public List<BackendConnectorDefinition> enabledConnectors() {
    return loadRegistry().connectors().stream()
        .filter(BackendConnectorDefinition::enabled)
        .toList();
  }

  public Optional<BackendConnectorDefinition> resolveConnector(String connectorId) {
    return enabledConnectors().stream()
        .filter(connector -> connector.id() != null && connector.id().equalsIgnoreCase(connectorId))
        .findFirst();
  }

  public Optional<ResolvedBackendTool> resolveTool(String namespacedToolName) {
    return enabledConnectors().stream()
        .flatMap(connector -> connector.toolCache().stream()
            .map(tool -> new ResolvedBackendTool(connector, tool)))
        .filter(tool -> tool.toolName().equalsIgnoreCase(namespacedToolName))
        .findFirst();
  }

  public Optional<McpServerProcessConfiguration> resolveProcessConfiguration(String connectorId) {
    return resolveConnector(connectorId)
        .filter(BackendConnectorDefinition::launchesOverStdio)
        .map(connector -> new McpServerProcessConfiguration(
            connector.id(),
            connector.command(),
            connector.args(),
            connector.env()
        ));
  }

  private BackendConnectorRegistry loadRegistry() {
    Path registryPath = resolveRegistryPath();
    if (registryPath == null || !Files.isRegularFile(registryPath)) {
      return new BackendConnectorRegistry(1, "tavall-ai", List.of());
    }
    try {
      return objectMapper.readValue(registryPath.toFile(), BackendConnectorRegistry.class);
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to read backend connector registry: " + registryPath, exception);
    }
  }

  private Path resolveRegistryPath() {
    String configuredPath = properties.getBackendRegistryPath();
    if (configuredPath == null || configuredPath.isBlank()) {
      return null;
    }
    return Path.of(configuredPath).toAbsolutePath().normalize();
  }

  public record ResolvedBackendTool(
      BackendConnectorDefinition connector,
      BackendToolDefinition tool
  ) {

    public String toolName() {
      return connector.id() + "." + tool.name();
    }
  }
}


