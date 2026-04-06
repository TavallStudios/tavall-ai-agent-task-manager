package org.tavall.ai.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.memory-sync")
public class MemorySyncProperties {

  private boolean enabled = true;
  private boolean managedRepoBackfillEnabled = true;
  private boolean workspaceSyncEnabled = true;
  private int outboxBatchSize = 50;
  private int maxRepoBackfillsPerCycle = 1;
  private int maxOutboxBatchesPerCycle = 4;
  private long pollIntervalMs = 15000;
  private long retryDelayMs = 15000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isManagedRepoBackfillEnabled() {
    return managedRepoBackfillEnabled;
  }

  public void setManagedRepoBackfillEnabled(boolean managedRepoBackfillEnabled) {
    this.managedRepoBackfillEnabled = managedRepoBackfillEnabled;
  }

  public boolean isWorkspaceSyncEnabled() {
    return workspaceSyncEnabled;
  }

  public void setWorkspaceSyncEnabled(boolean workspaceSyncEnabled) {
    this.workspaceSyncEnabled = workspaceSyncEnabled;
  }

  public int getOutboxBatchSize() {
    return outboxBatchSize;
  }

  public void setOutboxBatchSize(int outboxBatchSize) {
    this.outboxBatchSize = outboxBatchSize;
  }

  public int getMaxRepoBackfillsPerCycle() {
    return maxRepoBackfillsPerCycle;
  }

  public void setMaxRepoBackfillsPerCycle(int maxRepoBackfillsPerCycle) {
    this.maxRepoBackfillsPerCycle = maxRepoBackfillsPerCycle;
  }

  public int getMaxOutboxBatchesPerCycle() {
    return maxOutboxBatchesPerCycle;
  }

  public void setMaxOutboxBatchesPerCycle(int maxOutboxBatchesPerCycle) {
    this.maxOutboxBatchesPerCycle = maxOutboxBatchesPerCycle;
  }

  public long getPollIntervalMs() {
    return pollIntervalMs;
  }

  public void setPollIntervalMs(long pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs;
  }

  public long getRetryDelayMs() {
    return retryDelayMs;
  }

  public void setRetryDelayMs(long retryDelayMs) {
    this.retryDelayMs = retryDelayMs;
  }
}

