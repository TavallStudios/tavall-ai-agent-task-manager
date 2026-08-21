package org.tavall.ai.app.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the private ChatGPT tunnel ingress served by the existing
 * AgentTaskManager process.
 *
 * <p>The gateway is disabled unless explicitly enabled by deployment
 * configuration. Its Cloud CONTROL credentials stay file and socket paths;
 * secret material is never copied into Spring configuration.</p>
 */
@ConfigurationProperties(prefix = "app.chatgpt-mcp-gateway")
public class ChatGPTMcpGatewayProperties {

  private boolean enabled;
  private String controlNodeId = "";
  private Path controlSecretPath;
  private Path controlSocketPath;
  private int maximumFrameBytes = 4 * 1024 * 1024;
  private Path socketPath;
  private String socketGroup = "";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getControlNodeId() {
    return controlNodeId;
  }

  public void setControlNodeId(String controlNodeId) {
    this.controlNodeId = controlNodeId;
  }

  public Path getControlSecretPath() {
    return controlSecretPath;
  }

  public void setControlSecretPath(Path controlSecretPath) {
    this.controlSecretPath = controlSecretPath;
  }

  public Path getControlSocketPath() {
    return controlSocketPath;
  }

  public void setControlSocketPath(Path controlSocketPath) {
    this.controlSocketPath = controlSocketPath;
  }

  public int getMaximumFrameBytes() {
    return maximumFrameBytes;
  }

  public void setMaximumFrameBytes(int maximumFrameBytes) {
    this.maximumFrameBytes = maximumFrameBytes;
  }

  public Path getSocketPath() {
    return socketPath;
  }

  public void setSocketPath(Path socketPath) {
    this.socketPath = socketPath;
  }

  public String getSocketGroup() {
    return socketGroup;
  }

  public void setSocketGroup(String socketGroup) {
    this.socketGroup = socketGroup;
  }
}
