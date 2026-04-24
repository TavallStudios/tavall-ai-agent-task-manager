package org.tavall.ai.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.codex")
public class CodexExecutionProperties {

  private String mcpServerBinDir = "/srv/mcp-servers/bin";
  private String reasoningEffort = "high";
  private List<String> addDirectories = new ArrayList<>(List.of("/srv"));
  private String downstreamCentralServer = "tavall-ai";
  private List<String> requiredMcpServers = new ArrayList<>();
  private boolean centralServerLocalStdioEnabled;
  private String centralServerJarPath = "";
  private boolean remoteToolExecutionEnabled;

  public String getMcpServerBinDir() {
    return mcpServerBinDir;
  }

  public void setMcpServerBinDir(String mcpServerBinDir) {
    this.mcpServerBinDir = mcpServerBinDir;
  }

  public String getReasoningEffort() {
    return reasoningEffort;
  }

  public void setReasoningEffort(String reasoningEffort) {
    this.reasoningEffort = reasoningEffort;
  }

  public List<String> getAddDirectories() {
    return addDirectories;
  }

  public void setAddDirectories(List<String> addDirectories) {
    this.addDirectories = addDirectories;
  }

  public String getDownstreamCentralServer() {
    return downstreamCentralServer;
  }

  public void setDownstreamCentralServer(String downstreamCentralServer) {
    this.downstreamCentralServer = downstreamCentralServer;
  }

  public List<String> getRequiredMcpServers() {
    return requiredMcpServers;
  }

  public void setRequiredMcpServers(List<String> requiredMcpServers) {
    this.requiredMcpServers = requiredMcpServers;
  }

  public boolean isCentralServerLocalStdioEnabled() {
    return centralServerLocalStdioEnabled;
  }

  public void setCentralServerLocalStdioEnabled(boolean centralServerLocalStdioEnabled) {
    this.centralServerLocalStdioEnabled = centralServerLocalStdioEnabled;
  }

  public String getCentralServerJarPath() {
    return centralServerJarPath;
  }

  public void setCentralServerJarPath(String centralServerJarPath) {
    this.centralServerJarPath = centralServerJarPath;
  }

  public boolean isRemoteToolExecutionEnabled() {
    return remoteToolExecutionEnabled;
  }

  public void setRemoteToolExecutionEnabled(boolean remoteToolExecutionEnabled) {
    this.remoteToolExecutionEnabled = remoteToolExecutionEnabled;
  }
}


