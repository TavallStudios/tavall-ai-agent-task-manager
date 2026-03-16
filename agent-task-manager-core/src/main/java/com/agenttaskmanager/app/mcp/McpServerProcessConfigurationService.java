package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.config.CodexExecutionProperties;
import com.agenttaskmanager.app.persistence.qdrant.QdrantCollectionNameResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class McpServerProcessConfigurationService {

  private final CodexExecutionProperties properties;
  private final QdrantCollectionNameResolver collectionNameResolver;

  public McpServerProcessConfigurationService(
      CodexExecutionProperties properties,
      QdrantCollectionNameResolver collectionNameResolver
  ) {
    this.properties = properties;
    this.collectionNameResolver = collectionNameResolver;
  }

  public McpServerProcessConfiguration resolve(String serverName, String projectKey) {
    return switch (serverName) {
      case "clean-java-harness" -> javaModuleServer(
          serverName,
          "agent-task-manager-clean-java-harness/target/agent-task-manager-clean-java-harness-0.1.0-SNAPSHOT-exec.jar"
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
    if ("memory".equals(serverName)) {
      env.put("MEMORY_FILE_PATH", properties.getMemoryFilePath());
    }
    if ("qdrant".equals(serverName)) {
      env.put("COLLECTION_NAME", resolveQdrantCollection(projectKey));
    }
    return new McpServerProcessConfiguration(
        serverName,
        properties.getMcpServerBinDir() + "/" + serverName,
        List.of(),
        env
    );
  }

  private String resolveQdrantCollection(String projectKey) {
    if (projectKey == null || projectKey.isBlank()) {
      return collectionNameResolver.legacyCollection();
    }
    return collectionNameResolver.projectCollection(projectKey);
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
