package org.tavall.ai.app.mcp;

import java.io.File;
import org.tavall.ai.app.config.CodexExecutionProperties;
import org.tavall.ai.app.desktop.DesktopMcpServerMode;
import org.tavall.ai.app.desktop.DesktopMcpServerPreferenceCaps;
import org.tavall.ai.app.persistence.qdrant.QdrantCollectionNameResolver;
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
    return resolveLocal(serverName, projectKey, Map.of());
  }

  public List<McpServerProcessConfiguration> resolveCandidates(
      String serverName,
      String projectKey,
      DesktopMcpServerPreferenceCaps preference
  ) {
    DesktopMcpServerPreferenceCaps resolvedPreference = preference == null
        ? new DesktopMcpServerPreferenceCaps(true, DesktopMcpServerMode.LOCAL_ONLY, Map.of())
        : preference;
    DesktopMcpServerMode mode = resolvedPreference.mode();
    Map<String, String> overrides = resolvedPreference.envOverrides();
    McpServerProcessConfiguration local = resolveLocal(serverName, projectKey, overrides);
    var remote = resolveRemote(serverName, overrides);
    return switch (mode) {
      case LOCAL_ONLY -> List.of(local);
      case REMOTE_ONLY -> remote.map(List::of).orElseGet(List::of);
      case LOCAL_THEN_REMOTE -> remote.map(config -> List.of(local, config)).orElseGet(() -> List.of(local));
      case REMOTE_THEN_LOCAL -> remote.map(config -> List.of(config, local)).orElseGet(() -> List.of(local));
    };
  }

  private McpServerProcessConfiguration javaDistributionServer(
      String serverName,
      String relativeDistributionPath,
      String mainClass,
      List<String> applicationArguments
  ) {
    Path distributionPath = repoRoot().resolve(relativeDistributionPath).normalize();
    List<String> arguments = new ArrayList<>();
    arguments.add("--enable-preview");
    arguments.add("-cp");
    arguments.add(
        distributionPath.resolve("application.jar")
            + File.pathSeparator
            + distributionPath.resolve("libs/*")
    );
    arguments.add(mainClass);
    arguments.addAll(applicationArguments);
    return new McpServerProcessConfiguration(
        serverName,
        "java",
        List.copyOf(arguments),
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
    if (properties.getCentralServerJarPath() != null && !properties.getCentralServerJarPath().isBlank()) {
      Path jarPath = Path.of(properties.getCentralServerJarPath()).toAbsolutePath().normalize();
      return new McpServerProcessConfiguration(
          serverName,
          "java",
          List.of("--enable-preview", "-jar", jarPath.toString(), "serve-mcp-stdio"),
          remoteToolExecutionConfigurationService.environmentOverrides()
      );
    }
    McpServerProcessConfiguration configuration = javaDistributionServer(
        serverName,
        "distribution/agent-task-manager",
        "org.tavall.ai.app.AgentTaskManagerLauncher",
        List.of("serve-mcp-stdio")
    );
    return new McpServerProcessConfiguration(
        serverName,
        configuration.command(),
        configuration.args(),
        remoteToolExecutionConfigurationService.environmentOverrides()
    );
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
      if (Files.isRegularFile(current.resolve("settings.gradle.kts"))
          && Files.isDirectory(current.resolve("tavall-ai-core"))
          && Files.isDirectory(current.resolve("tavall-ai-app"))) {
        return current;
      }
      current = current.getParent();
    }
    return Path.of(".").toAbsolutePath().normalize();
  }

  private McpServerProcessConfiguration resolveLocal(
      String serverName,
      String projectKey,
      Map<String, String> envOverrides
  ) {
    if (isLocalCentralServer(serverName)) {
      return applyEnvOverrides(localCentralServer(serverName), envOverrides);
    }
    return switch (serverName) {
      case "tjai-harness", "clean-java-harness" -> throw new IllegalArgumentException(
          "tjai-harness (clean-java-harness compatibility alias) is bundled as a local validator/runtime dependency and is no longer launched as an MCP server."
      );
      case "clean-java-mcp" -> applyEnvOverrides(
          javaDistributionServer(
              serverName,
              "distribution/clean-java-mcp",
              "org.tavall.ai.app.cleanjava.CleanJavaMcpLauncher",
              List.of()
          ),
          envOverrides
      );
      default -> applyEnvOverrides(binaryServer(serverName, projectKey), envOverrides);
    };
  }

  private java.util.Optional<McpServerProcessConfiguration> resolveRemote(
      String serverName,
      Map<String, String> envOverrides
  ) {
    return backendConnectorRegistryService.resolveProcessConfiguration(serverName)
        .map(configuration -> applyEnvOverrides(configuration, envOverrides));
  }

  private McpServerProcessConfiguration applyEnvOverrides(
      McpServerProcessConfiguration base,
      Map<String, String> overrides
  ) {
    if (overrides == null || overrides.isEmpty()) {
      return base;
    }
    Map<String, String> merged = new LinkedHashMap<>(base.env());
    for (Map.Entry<String, String> entry : overrides.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || key.isBlank() || value == null) {
        continue;
      }
      merged.put(key, value);
    }
    return new McpServerProcessConfiguration(base.serverName(), base.command(), base.args(), merged);
  }
}

