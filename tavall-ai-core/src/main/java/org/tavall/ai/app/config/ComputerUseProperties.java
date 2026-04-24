package org.tavall.ai.app.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.computer-use")
public class ComputerUseProperties {

  private String runnerCommandPath = "/api/automation/command";
  private String runnerCapabilitiesPath = "/api/automation/capabilities";
  private String runnerAuthToken = "";
  private int runnerLeaseTtlSeconds = 120;
  private int visionPollIntervalMs = 400;
  private final HytaleProfile hytale = new HytaleProfile();

  public String getRunnerCommandPath() {
    return runnerCommandPath;
  }

  public void setRunnerCommandPath(String runnerCommandPath) {
    this.runnerCommandPath = runnerCommandPath;
  }

  public String getRunnerCapabilitiesPath() {
    return runnerCapabilitiesPath;
  }

  public void setRunnerCapabilitiesPath(String runnerCapabilitiesPath) {
    this.runnerCapabilitiesPath = runnerCapabilitiesPath;
  }

  public String getRunnerAuthToken() {
    return runnerAuthToken;
  }

  public void setRunnerAuthToken(String runnerAuthToken) {
    this.runnerAuthToken = runnerAuthToken;
  }

  public int getRunnerLeaseTtlSeconds() {
    return runnerLeaseTtlSeconds;
  }

  public void setRunnerLeaseTtlSeconds(int runnerLeaseTtlSeconds) {
    this.runnerLeaseTtlSeconds = runnerLeaseTtlSeconds;
  }

  public int getVisionPollIntervalMs() {
    return visionPollIntervalMs;
  }

  public void setVisionPollIntervalMs(int visionPollIntervalMs) {
    this.visionPollIntervalMs = visionPollIntervalMs;
  }

  public HytaleProfile getHytale() {
    return hytale;
  }

  public static final class HytaleProfile {

    private String launcherPath = "";
    private String clientPath = "";
    private String launcherProcessName = "HytaleLauncher";
    private String clientProcessName = "Hytale";
    private String launcherWindowTitleContains = "Hytale";
    private String clientWindowTitleContains = "Hytale";
    private String serverTarget = "Remote Dev";
    private String defaultChartId = "debug/test-4k";
    private final Map<String, String> visualAnchors = new LinkedHashMap<>();
    private final Map<String, String> gameplayKeybinds = new LinkedHashMap<>();

    public HytaleProfile() {
      visualAnchors.put("launcherReady", "");
      visualAnchors.put("clientReady", "");
      visualAnchors.put("worldJoined", "");
      visualAnchors.put("songSelect", "");
      visualAnchors.put("gameplayAssets", "");
      gameplayKeybinds.put("lane1", "D");
      gameplayKeybinds.put("lane2", "F");
      gameplayKeybinds.put("lane3", "J");
      gameplayKeybinds.put("lane4", "K");
    }

    public String getLauncherPath() {
      return launcherPath;
    }

    public void setLauncherPath(String launcherPath) {
      this.launcherPath = launcherPath;
    }

    public String getClientPath() {
      return clientPath;
    }

    public void setClientPath(String clientPath) {
      this.clientPath = clientPath;
    }

    public String getLauncherProcessName() {
      return launcherProcessName;
    }

    public void setLauncherProcessName(String launcherProcessName) {
      this.launcherProcessName = launcherProcessName;
    }

    public String getClientProcessName() {
      return clientProcessName;
    }

    public void setClientProcessName(String clientProcessName) {
      this.clientProcessName = clientProcessName;
    }

    public String getLauncherWindowTitleContains() {
      return launcherWindowTitleContains;
    }

    public void setLauncherWindowTitleContains(String launcherWindowTitleContains) {
      this.launcherWindowTitleContains = launcherWindowTitleContains;
    }

    public String getClientWindowTitleContains() {
      return clientWindowTitleContains;
    }

    public void setClientWindowTitleContains(String clientWindowTitleContains) {
      this.clientWindowTitleContains = clientWindowTitleContains;
    }

    public String getServerTarget() {
      return serverTarget;
    }

    public void setServerTarget(String serverTarget) {
      this.serverTarget = serverTarget;
    }

    public String getDefaultChartId() {
      return defaultChartId;
    }

    public void setDefaultChartId(String defaultChartId) {
      this.defaultChartId = defaultChartId;
    }

    public Map<String, String> getVisualAnchors() {
      return visualAnchors;
    }

    public Map<String, String> getGameplayKeybinds() {
      return gameplayKeybinds;
    }
  }
}

