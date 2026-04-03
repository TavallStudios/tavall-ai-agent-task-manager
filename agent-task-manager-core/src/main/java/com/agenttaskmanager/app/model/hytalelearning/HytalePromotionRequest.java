package com.agenttaskmanager.app.model.hytalelearning;

import java.util.Map;

public record HytalePromotionRequest(
    String sessionId,
    String subjectType,
    String subjectId,
    String semanticKind,
    String summary,
    String body,
    Map<String, Object> metadata
) {
}
