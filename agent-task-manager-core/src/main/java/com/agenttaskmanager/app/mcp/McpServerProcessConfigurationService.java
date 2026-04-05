package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.config.CodexExecutionProperties;
import com.agenttaskmanager.app.persistence.qdrant.QdrantCollectionNameResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class McpServerProcessConfigurationService {

  private final CodexExecutionProperties properties;
  private final BackendConnectorRegistryService backendConnectorRegistryService;
  private final QdrantCollectionNameResolver collectionNameResolver;
  private final RemoteToolExecutionConfigurationService remoteToolExecutionConfigurationService;

  public McpServerProcessConfigurationService(
      CodexExecutionProperties properties,
      BackendConnectorRegistryService backendConnectorRegistryService,
      QdrantCollectionNameResolver collectionNameResolver,
      RemoteToolExecutionConfigurationService remoteToolExecutionConfigurationService
  ) {
    this.properties = properties;
    this.backendConnectorRegistryService = backendConnectorRegistryService;
    this.collectionNameResolver = collectionNameResolver;
    this.remoteToolExecutionConfigurationService = remoteToolExecutionConfigurationService;
  }

  public McpServerProcessConfiguration resolve(String serverName, String projectKey) {
    if (isLocalCentralServer(serverName)) {
      return localCentralServer(serverName);
    }
    var backendConfiguration = backendConnectorRegistryService.resolveProcessConfiguration(serverName);
    if (backendConfiguration.isPresent()) {
      return backendConfiguration.get();
    }
    return switch (serverName) {
      case "tjai-harness", "clean-java-harness" -> throw new IllegalArgumentException(
          "tjai-harness (clean-java-harness compatibility alias) is bundled as a local validator/runtime dependency and is no longer launched as an MCP server."
      );
      case "clean-java-mcp" -> javaModuleServer(
          serverName,
          "agent-task-manager-clean-java-mcp/target/agent-task-manager-clean-java-mcp-0.1.0-SNAPSHOT-exec.jar"
      );
      default -> binaryServer(serverName, projectKey);
    };
  }

  private McpServerProcessConfiguration javaModuleServer(String serverName, String relativeJarPath) {
    Path jarPath = repoRoot().resolve(relativeJarPath).normalize();
    return new McpServerProcessConfiguration(
        serverName,
        "java",
        List.of("-jar", jarPath.toString()),
        Map.of()
    );
  }

  private McpServerProcessConfiguration binaryServer(String serverName, String projectKey) {
    Map<String, String> env = new LinkedHashMap<>();
    if ("qdrant".equals(serverName)) {
      env.put("COLLECTION_NAME", resolveQdrantCollection(projectKey));
    }
    return new McpServerProcessConfiguration(
        serverName,
        resolveBinaryCommand(serverName),
        List.of(),
        env
    );
  }

  private boolean isLocalCentralServer(String serverName) {
    if (!properties.isCentralServerLocalStdioEnabled()) {
      return false;
    }
    String centralServer = properties.getDownstreamCentralServer();
    if (centralServer == null || centralServer.isBlank() || serverName == null || serverName.isBlank()) {
      return false;
    }
    return centralServer.strip().equals(serverName.strip());
  }

  private McpServerProcessConfiguration localCentralServer(String serverName) {
    Path jarPath = resolveCentralServerJarPath();
    return new McpServerProcessConfiguration(
        serverName,
        "java",
        List.of("-jar", jarPath.toString(), "serve-mcp-stdio"),
        remoteToolExecutionConfigurationService.environmentOverrides()
    );
  }

  private Path resolveCentralServerJarPath() {
    if (properties.getCentralServerJarPath() != null && !properties.getCentralServerJarPath().isBlank()) {
      return Path.of(properties.getCentralServerJarPath()).toAbsolutePath().normalize();
    }
    return repoRoot()
        .resolve("agent-task-manager-app/target/agent-task-manager-app-0.1.0-SNAPSHOT.jar")
        .normalize();
  }

  private String resolveQdrantCollection(String projectKey) {
    if (projectKey == null || projectKey.isBlank()) {
      return collectionNameResolver.projectCollection("default");
    }
    return collectionNameResolver.projectCollection(projectKey);
  }

  private String resolveBinaryCommand(String serverName) {
    for (Path candidate : binaryCandidates(serverName)) {
      if (Files.isRegularFile(candidate)) {
        return candidate.toString();
      }
    }
    return serverName;
  }

  private List<Path> binaryCandidates(String serverName) {
    List<Path> candidates = new ArrayList<>();
    addBinaryCandidates(candidates, repoRoot().resolve("mcp-servers/bin"), serverName);
    if (properties.getMcpServerBinDir() != null && !properties.getMcpServerBinDir().isBlank()) {
      addBinaryCandidates(candidates, Path.of(properties.getMcpServerBinDir()), serverName);
    }
    return candidates;
  }

  private void addBinaryCandidates(List<Path> candidates, Path root, String serverName) {
    String[] suffixes = isWindows()
        ? new String[]{".cmd", ".bat", ".exe", ""}
        : new String[]{"", ".sh"};
    for (String suffix : suffixes) {
      candidates.add(root.resolve(serverName + suffix).normalize());
    }
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  private Path repoRoot() {
    Path current = Path.of(".").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("AGENTS.md"))
          && Files.isRegularFile(current.resolve("pom.xml"))
          && Files.isDirectory(current.resolve("agent-task-manager-core"))
          && Files.isDirectory(current.resolve("agent-task-manager-app"))) {
        return current;
      }
      current = current.getParent();
    }
    return Path.of(".").toAbsolutePath().normalize();
  }
}
