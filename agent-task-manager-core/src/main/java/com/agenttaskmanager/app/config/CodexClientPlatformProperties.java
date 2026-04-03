package com.agenttaskmanager.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.codex-client-platform")
public class CodexClientPlatformProperties {

  private boolean enabled = false;
  private String preferredModel = "gpt-5.3-codex";
  private String preferredReasoningEffort = "high";
  private String defaultProfileKey = "workspace-default";
  private int defaultEventPageSize = 100;
  private int maxEventPageSize = 500;
  private int runtimeLeaseTtlSeconds = 90;
  private int sseReplayLimit = 50;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getPreferredModel() {
    return preferredModel;
  }

  public void setPreferredModel(String preferredModel) {
    this.preferredModel = preferredModel;
  }

  public String getPreferredReasoningEffort() {
    return preferredReasoningEffort;
  }

  public void setPreferredReasoningEffort(String preferredReasoningEffort) {
    this.preferredReasoningEffort = preferredReasoningEffort;
  }

  public String getDefaultProfileKey() {
    return defaultProfileKey;
  }

  public void setDefaultProfileKey(String defaultProfileKey) {
    this.defaultProfileKey = defaultProfileKey;
  }

  public int getDefaultEventPageSize() {
    return defaultEventPageSize;
  }

  public void setDefaultEventPageSize(int defaultEventPageSize) {
    this.defaultEventPageSize = defaultEventPageSize;
  }

  public int getMaxEventPageSize() {
    return maxEventPageSize;
  }

  public void setMaxEventPageSize(int maxEventPageSize) {
    this.maxEventPageSize = maxEventPageSize;
  }

  public int getRuntimeLeaseTtlSeconds() {
    return runtimeLeaseTtlSeconds;
  }

  public void setRuntimeLeaseTtlSeconds(int runtimeLeaseTtlSeconds) {
    this.runtimeLeaseTtlSeconds = runtimeLeaseTtlSeconds;
  }

  public int getSseReplayLimit() {
    return sseReplayLimit;
  }

  public void setSseReplayLimit(int sseReplayLimit) {
    this.sseReplayLimit = sseReplayLimit;
  }
}
