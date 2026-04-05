package com.agenttaskmanager.app.persistence.postgres;

import java.time.OffsetDateTime;
import java.util.Map;

public record SemanticSyncOutboxEntry(
    String outboxId,
    String dedupeKey,
    String operationKind,
    String scopeKey,
    String documentId,
    String taskId,
    String workerTaskId,
    String semanticKind,
    String title,
    String content,
    String domain,
    String contentType,
    Map<String, Object> payload,
    Map<String, Object> payloadFilter,
    String status,
    int attemptCount,
    String lastError,
    OffsetDateTime availableAt
) {
}
