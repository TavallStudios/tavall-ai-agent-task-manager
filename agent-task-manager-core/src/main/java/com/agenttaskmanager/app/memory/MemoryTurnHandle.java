package com.agenttaskmanager.app.memory;

public record MemoryTurnHandle(
    String requestId,
    String ingressEventId,
    String lookupEventId,
    String requestText,
    MemoryIdentity identity,
    MemoryHydration hydration
) {
}
