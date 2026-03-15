package com.agenttaskmanager.app.model.orchestration;

import java.util.Map;

public record RetrievedSemanticContext(
    String id,
    double score,
    Map<String, Object> payload
) {
}
