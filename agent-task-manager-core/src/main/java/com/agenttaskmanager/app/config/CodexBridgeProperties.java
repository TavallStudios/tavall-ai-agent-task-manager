package com.agenttaskmanager.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bridge")
public class CodexBridgeProperties {

  private boolean enabled = true;
  private String agentId = "";
  private String command = "codex";
  private long pollIntervalMs = 5000;
  private int maxMessageChars = 4000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(String agentId) {
    this.agentId = agentId;
  }

  public String getCommand() {
    return command;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public long getPollIntervalMs() {
    return pollIntervalMs;
  }

  public void setPollIntervalMs(long pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs;
  }

  public int getMaxMessageChars() {
    return maxMessageChars;
  }

  public void setMaxMessageChars(int maxMessageChars) {
    this.maxMessageChars = maxMessageChars;
  }
}
