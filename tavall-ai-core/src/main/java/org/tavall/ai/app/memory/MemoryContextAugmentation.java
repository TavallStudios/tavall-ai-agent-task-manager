package org.tavall.ai.app.memory;

import java.util.List;

public record MemoryContextAugmentation(
    String summary,
    String section,
    List<MemoryKnowledgeContext> contexts
) {

  public MemoryContextAugmentation {
    summary = summary == null ? "" : summary.strip();
    section = section == null ? "" : section.strip();
    contexts = contexts == null ? List.of() : List.copyOf(contexts);
  }
}
