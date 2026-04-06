package org.tavall.ai.app.memory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record MemoryContinuitySnapshot(
    String continuitySnapshotId,
    String userId,
    String workspaceId,
    String projectId,
    String chatId,
    String threadKey,
    String sessionId,
    String apiKeyId,
    String requestId,
    String summary,
    List<Map<String, Object>> workingMemory,
    List<String> memoryIds,
    Map<String, Object> sourceCounts,
    Map<String, Object> metadata,
    OffsetDateTime updatedAt
) {
}

