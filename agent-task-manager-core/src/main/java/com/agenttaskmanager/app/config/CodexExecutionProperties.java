package com.agenttaskmanager.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.codex")
public class CodexExecutionProperties {

  private String mcpServerBinDir = "/srv/mcp-servers/bin";
  private String memoryFilePath = "/srv/.codex-memory/global-memory.jsonl";
  private String reasoningEffort = "high";
  private List<String> addDirectories = new ArrayList<>(List.of("/srv", "/srv/local-pc-root"));
  private List<String> requiredMcpServers = new ArrayList<>(
      List.of(
          "filesystem",
          "ripgrep",
          "git",
          "memory",
          "qdrant",
          "redis",
          "postgres",
          "mongodb",
          "mcp-catalog",
          "clean-java-mcp",
          "clean-java-harness"
      )
  );

  public String getMcpServerBinDir() {
    return mcpServerBinDir;
  }

  public void setMcpServerBinDir(String mcpServerBinDir) {
    this.mcpServerBinDir = mcpServerBinDir;
  }

  public String getMemoryFilePath() {
    return memoryFilePath;
  }

  public void setMemoryFilePath(String memoryFilePath) {
    this.memoryFilePath = memoryFilePath;
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

  public List<String> getRequiredMcpServers() {
    return requiredMcpServers;
  }

  public void setRequiredMcpServers(List<String> requiredMcpServers) {
    this.requiredMcpServers = requiredMcpServers;
  }
}
