package org.tavall.ai.app.memory;

import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import java.util.List;

public record MemoryHydration(
    String summary,
    String section,
    List<MemoryRecord> exactRecords,
    List<RetrievedSemanticContext> semanticCandidates,
    List<MemoryKnowledgeContext> providerContexts
) {

  public MemoryHydration {
    providerContexts = providerContexts == null ? List.of() : List.copyOf(providerContexts);
  }
}
