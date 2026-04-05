package com.agenttaskmanager.app.memory;

import java.util.List;
import java.util.Map;

public record MemoryMutationPlan(
    MemoryAction action,
    MemoryScope scope,
    MemoryKind kind,
    String title,
    String titleKey,
    String summary,
    List<String> facts,
    int importance,
    String sensitivity,
    String consentLevel,
    Map<String, Object> metadata
) {
}
