package com.agenttaskmanager.app.model.hytalelearning;

import java.util.Map;

public record HytaleActionTraceRequest(
    String commandRequestId,
    String actionKind,
    String commandId,
    String status,
    String summary,
    Map<String, Object> payload
) {
}
