package org.tavall.ai.context;

/** Provider-neutral classes of durable project context that may be attached to model execution. */
public enum TavallAIContextKind {
    CHAT,
    MEMORY,
    FILE,
    INSTRUCTION,
    PROJECT_METADATA
}
