package com.agenttaskmanager.app.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BackendConnectorDefinition(
    String id,
    String displayName,
    boolean enabled,
    String transportKind,
    String command,
    List<String> args,
    String url,
    Map<String, String> env,
    String source,
    String healthStatus,
    List<BackendToolDefinition> toolCache
) {

  public BackendConnectorDefinition {
    args = args == null ? List.of() : List.copyOf(args);
    env = env == null ? Map.of() : Map.copyOf(env);
    toolCache = toolCache == null ? List.of() : List.copyOf(toolCache);
  }

  public String resolvedDisplayName() {
    if (displayName != null && !displayName.isBlank()) {
      return displayName;
    }
    return id == null ? "backend" : id;
  }

  public boolean launchesOverStdio() {
    return enabled && command != null && !command.isBlank()
        && (transportKind == null || transportKind.isBlank() || "stdio".equalsIgnoreCase(transportKind));
  }
}
