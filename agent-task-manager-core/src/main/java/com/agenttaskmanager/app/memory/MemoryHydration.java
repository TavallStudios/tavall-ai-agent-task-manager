package com.agenttaskmanager.app.memory;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import java.util.List;

public record MemoryHydration(
    String summary,
    String section,
    List<MemoryRecord> exactRecords,
    List<RetrievedSemanticContext> semanticCandidates
) {
}
