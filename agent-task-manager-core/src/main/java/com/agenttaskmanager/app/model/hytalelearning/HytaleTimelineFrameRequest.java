package com.agenttaskmanager.app.model.hytalelearning;

import java.util.Map;

public record HytaleTimelineFrameRequest(
    String sourceWindow,
    String artifactKind,
    String summary,
    String base64Body,
    Map<String, Object> metadata
) {
}
