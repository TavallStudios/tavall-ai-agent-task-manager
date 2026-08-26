package org.tavall.ai.app.memory;

import java.util.List;
import java.util.Map;

public record MemoryKnowledgeContext(
    String provider,
    MemoryKnowledgeRole role,
    String content,
    List<String> evidenceReferences,
    Map<String, Object> metadata,
    long latencyMillis,
    boolean degraded,
    String error
) {

  public MemoryKnowledgeContext {
    provider = provider == null ? "" : provider.strip();
    content = content == null ? "" : content.strip();
    evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    error = error == null ? "" : error.strip();
  }

  public static MemoryKnowledgeContext disabled(String provider, MemoryKnowledgeRole role) {
    return new MemoryKnowledgeContext(provider, role, "", List.of(), Map.of("configured", false), 0L, false, "");
  }

  public static MemoryKnowledgeContext failed(
      String provider,
      MemoryKnowledgeRole role,
      long latencyMillis,
      RuntimeException exception
  ) {
    return new MemoryKnowledgeContext(
        provider,
        role,
        "",
        List.of(),
        Map.of("configured", true),
        latencyMillis,
        true,
        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
    );
  }
}
