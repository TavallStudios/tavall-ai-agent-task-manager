package org.tavall.ai.app.model;

import java.time.OffsetDateTime;

public record PromptMessage(
    long messageId,
    Long runId,
    String messageKind,
    String senderName,
    String body,
    OffsetDateTime createdAt
) {
}


