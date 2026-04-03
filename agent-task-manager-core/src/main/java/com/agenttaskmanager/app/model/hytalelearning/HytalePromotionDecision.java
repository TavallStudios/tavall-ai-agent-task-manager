package com.agenttaskmanager.app.model.hytalelearning;

import java.time.OffsetDateTime;
import java.util.Map;

public record HytalePromotionDecision(
    String decisionId,
    String sessionId,
    String subjectType,
    String subjectId,
    String semanticKind,
    String decisionStatus,
    String summary,
    String promotedDocumentId,
    Map<String, Object> metadata,
    OffsetDateTime createdAt
) {
}
