package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.config.CodexExecutionProperties;
import com.agenttaskmanager.app.persistence.qdrant.QdrantCollectionNameResolver;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CodexDeterministicConfigService {

  private final CodexExecutionProperties properties;
  private final QdrantCollectionNameResolver collectionNameResolver;

  public CodexDeterministicConfigService(
      CodexExecutionProperties properties,
      QdrantCollectionNameResolver collectionNameResolver
  ) {
    this.properties = properties;
    this.collectionNameResolver = collectionNameResolver;
  }

  public void appendDeterministicArguments(List<String> command, String projectKey) {
    appendConfig(command, "model_reasoning_effort", tomlString(properties.getReasoningEffort()));
    for (String serverName : properties.getRequiredMcpServers()) {
      appendConfig(
          command,
          "mcp_servers." + serverName + ".command",
          tomlString(resolveServerCommand(serverName))
      );
      if (usesShellWrapper(serverName)) {
        appendConfig(
            command,
            "mcp_servers." + serverName + ".args",
            tomlArray(properties.getMcpServerBinDir() + "/" + serverName)
        );
      }
      if ("memory".equals(serverName)) {
        appendConfig(
            command,
            "mcp_servers.memory.env.MEMORY_FILE_PATH",
            tomlString(properties.getMemoryFilePath())
        );
      }
      if ("qdrant".equals(serverName)) {
        appendConfig(
            command,
            "mcp_servers.qdrant.env.COLLECTION_NAME",
            tomlString(resolveQdrantCollection(projectKey))
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

  private String resolveQdrantCollection(String projectKey) {
    if (projectKey == null || projectKey.isBlank()) {
      return collectionNameResolver.legacyCollection();
    }
    return collectionNameResolver.projectCollection(projectKey);
  }

  private String resolveServerCommand(String serverName) {
    if (usesShellWrapper(serverName)) {
      return "/bin/bash";
    }
    return properties.getMcpServerBinDir() + "/" + serverName;
  }

  private static boolean usesShellWrapper(String serverName) {
    return "clean-java-mcp".equals(serverName) || "clean-java-harness".equals(serverName);
  }

  private static void appendConfig(List<String> command, String key, String value) {
    command.add("-c");
    command.add(key + "=" + value);
  }

  private static String tomlArray(String value) {
    return "[" + tomlString(value) + "]";
  }

  private static String tomlString(String value) {
    String normalized = value == null ? "" : value;
    return '"' + normalized.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
  }
}
