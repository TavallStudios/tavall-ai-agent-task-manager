package org.tavall.ai.app.memory;

import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import java.util.List;

public record MemoryHydration(
    String summary,
    String section,
    List<MemoryRecord> exactRecords,
    List<RetrievedSemanticContext> semanticCandidates
) {
}

