package com.agenttaskmanager.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.orchestration")
public class OrchestrationProperties {

  private String workerCommand = "codex";
  private String workerModel = "gpt-5.3-codex";
  private String overseerAgentId = "overseer@project-novus";
  private String cleanupAgentId = "cleanup@project-novus";
  private boolean autonomyEnabled = true;
  private int autonomyPollIntervalMs = 15000;
  private int autonomyMaxBatchCountPerCycle = 4;
  private int autonomyMaxWorkerRunsPerCycle = 4;
  private int leaseDurationSeconds = 90;
  private int checkInTimeoutSeconds = 120;

  public String getWorkerCommand() {
    return workerCommand;
  }

  public void setWorkerCommand(String workerCommand) {
    this.workerCommand = workerCommand;
  }

  public String getWorkerModel() {
    return workerModel;
  }

  public void setWorkerModel(String workerModel) {
    this.workerModel = workerModel;
  }

  public String getOverseerAgentId() {
    return overseerAgentId;
  }

  public void setOverseerAgentId(String overseerAgentId) {
    this.overseerAgentId = overseerAgentId;
  }

  public String getCleanupAgentId() {
    return cleanupAgentId;
  }

  public void setCleanupAgentId(String cleanupAgentId) {
    this.cleanupAgentId = cleanupAgentId;
  }

  public boolean isAutonomyEnabled() {
    return autonomyEnabled;
  }

  public void setAutonomyEnabled(boolean autonomyEnabled) {
    this.autonomyEnabled = autonomyEnabled;
  }

  public int getAutonomyPollIntervalMs() {
    return autonomyPollIntervalMs;
  }

  public void setAutonomyPollIntervalMs(int autonomyPollIntervalMs) {
    this.autonomyPollIntervalMs = autonomyPollIntervalMs;
  }

  public int getAutonomyMaxBatchCountPerCycle() {
    return autonomyMaxBatchCountPerCycle;
  }

  public void setAutonomyMaxBatchCountPerCycle(int autonomyMaxBatchCountPerCycle) {
    this.autonomyMaxBatchCountPerCycle = autonomyMaxBatchCountPerCycle;
  }

  public int getAutonomyMaxWorkerRunsPerCycle() {
    return autonomyMaxWorkerRunsPerCycle;
  }

  public void setAutonomyMaxWorkerRunsPerCycle(int autonomyMaxWorkerRunsPerCycle) {
    this.autonomyMaxWorkerRunsPerCycle = autonomyMaxWorkerRunsPerCycle;
  }

  public int getLeaseDurationSeconds() {
    return leaseDurationSeconds;
  }

  public void setLeaseDurationSeconds(int leaseDurationSeconds) {
    this.leaseDurationSeconds = leaseDurationSeconds;
  }

  public int getCheckInTimeoutSeconds() {
    return checkInTimeoutSeconds;
  }

  public void setCheckInTimeoutSeconds(int checkInTimeoutSeconds) {
    this.checkInTimeoutSeconds = checkInTimeoutSeconds;
  }
}
