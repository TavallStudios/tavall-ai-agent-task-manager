package com.agenttaskmanager.app.memory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record MemoryRecord(
    String memoryId,
    String userId,
    String workspaceId,
    String sessionId,
    String chatId,
    String requestId,
    String projectId,
    String threadKey,
    MemoryScope scope,
    MemoryKind kind,
    String title,
    String titleKey,
    String summary,
    List<String> facts,
    List<String> sourceEventIds,
    int version,
    String status,
    int importance,
    String sensitivity,
    String consentLevel,
    String supersededBy,
    boolean tombstoned,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
