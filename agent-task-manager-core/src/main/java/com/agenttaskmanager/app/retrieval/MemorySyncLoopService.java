package com.agenttaskmanager.app.retrieval;

import com.agenttaskmanager.app.config.MemorySyncProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MemorySyncLoopService {

  private final MemorySyncProperties properties;
  private final RepoSemanticSyncService repoSemanticSyncService;
  private final SemanticSyncService semanticSyncService;

  public MemorySyncLoopService(
      MemorySyncProperties properties,
      RepoSemanticSyncService repoSemanticSyncService,
      SemanticSyncService semanticSyncService
  ) {
    this.properties = properties;
    this.repoSemanticSyncService = repoSemanticSyncService;
    this.semanticSyncService = semanticSyncService;
  }

  @Scheduled(
      initialDelayString = "${app.memory-sync.poll-interval-ms:15000}",
      fixedDelayString = "${app.memory-sync.poll-interval-ms:15000}"
  )
  public void runSyncLoop() {
    if (!properties.isEnabled()) {
      return;
    }
    drainPendingOperations();
    int syncedRepos = repoSemanticSyncService.syncManagedRepos();
    if (syncedRepos > 0 || semanticSyncService.pendingCount() > 0) {
      drainPendingOperations();
    }
  }

  private void drainPendingOperations() {
    for (int batch = 0; batch < Math.max(1, properties.getMaxOutboxBatchesPerCycle()); batch++) {
      if (semanticSyncService.processPendingOperations() <= 0) {
        return;
      }
    }
  }
}
