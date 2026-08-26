package org.tavall.ai.app.memory;

import java.util.List;
import java.util.Map;

public record MemoryWriteRequest(
    MemoryScope scope,
    MemoryKind kind,
    String title,
    String summary,
    List<String> facts,
    Integer importance,
    String sensitivity,
    String consentLevel,
    String sourceReference,
    String supersedesMemoryId,
    Map<String, Object> metadata
) {
}
