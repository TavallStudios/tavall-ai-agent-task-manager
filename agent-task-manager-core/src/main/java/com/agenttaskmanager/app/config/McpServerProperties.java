package com.agenttaskmanager.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mcp")
public class McpServerProperties {

  private String baseUrl = "https://docs.tavall.org/agent-task-manager";
  private String endpoint = "/mcp";
  private List<String> toolGroups = new ArrayList<>();

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public List<String> getToolGroups() {
    return toolGroups;
  }

  public void setToolGroups(List<String> toolGroups) {
    this.toolGroups = toolGroups;
  }
}
