package org.tavall.ai.app.mcp;

import org.tavall.ai.app.config.CodexExecutionProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.tavall.ai.app.config.McpServerProperties;
import org.tavall.ai.app.config.SecurityProperties;
import org.springframework.stereotype.Service;

@Service
public class RemoteToolExecutionConfigurationService {

  private final CodexExecutionProperties properties;
  private final McpServerProperties mcpServerProperties;
  private final SecurityProperties securityProperties;

  public RemoteToolExecutionConfigurationService(
      CodexExecutionProperties properties,
      McpServerProperties mcpServerProperties,
      SecurityProperties securityProperties
  ) {
    this.properties = properties;
    this.mcpServerProperties = mcpServerProperties;
    this.securityProperties = securityProperties;
  }

  public boolean isRemoteExecutionEnabled() {
    return properties.isRemoteToolExecutionEnabled();
  }

  public Map<String, String> environmentOverrides() {
    Map<String, String> env = new LinkedHashMap<>();
    env.put("AGENT_TASK_MANAGER_CODEX_DOWNSTREAM_CENTRAL_SERVER", "");
    env.put(
        "AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED",
        String.valueOf(properties.isRemoteToolExecutionEnabled())
    );
    putIfPresent(env, "AGENT_TASK_MANAGER_MCP_BASE_URL", mcpServerProperties.getBaseUrl());
    putIfPresent(env, "AGENT_TASK_MANAGER_MCP_ENDPOINT", mcpServerProperties.getEndpoint());
    putIfPresent(env, "AGENT_TASK_MANAGER_USERNAME", securityProperties.getUsername());
    putIfPresent(env, "AGENT_TASK_MANAGER_PASSWORD", securityProperties.getPassword());
    return env;
  }

  private void putIfPresent(Map<String, String> env, String key, String value) {
    if (value != null && !value.isBlank()) {
      env.put(key, value);
    }
  }
}

