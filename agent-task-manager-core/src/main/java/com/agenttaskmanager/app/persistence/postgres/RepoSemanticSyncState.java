package com.agenttaskmanager.app.persistence.postgres;

import java.time.OffsetDateTime;

public record RepoSemanticSyncState(
    String projectKey,
    String repoPath,
    String lastSyncedHead,
    OffsetDateTime lastSyncedAt,
    OffsetDateTime lastScanStartedAt,
    OffsetDateTime lastScanCompletedAt,
    String lastError
) {
}
