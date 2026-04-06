package org.tavall.ai.app.model.hytalelearning;

import java.time.OffsetDateTime;
import java.util.Map;

public record HytaleActionTrace(
    String traceId,
    String sessionId,
    String commandRequestId,
    String actionKind,
    String commandId,
    String status,
    String summary,
    Map<String, Object> payload,
    OffsetDateTime createdAt
) {
}

