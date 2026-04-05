package com.agenttaskmanager.app.retrieval;

import com.agenttaskmanager.app.persistence.qdrant.QdrantContextStore;
import org.springframework.stereotype.Service;

@Service
public class QdrantHealthService {

  private final QdrantContextStore qdrantContextStore;

  public QdrantHealthService(QdrantContextStore qdrantContextStore) {
    this.qdrantContextStore = qdrantContextStore;
  }

  public Snapshot currentSnapshot() {
    boolean configured = qdrantContextStore.isConfigured();
    boolean fallbackEnabled = qdrantContextStore.isLocalFallbackEnabled();
    if (!configured) {
      return new Snapshot("DEGRADED", "Qdrant base URL is not configured.", false, false);
    }
    if (fallbackEnabled) {
      return new Snapshot("DEGRADED", "Qdrant is unavailable and the runtime is using local fallback.", true, false);
    }
    return new Snapshot("HEALTHY", "Qdrant write-through is healthy.", true, true);
  }

  public boolean isWriteThroughHealthy() {
    return currentSnapshot().writeThroughHealthy();
  }

  public record Snapshot(
      String status,
      String summary,
      boolean configured,
      boolean writeThroughHealthy
  ) {
  }
}
