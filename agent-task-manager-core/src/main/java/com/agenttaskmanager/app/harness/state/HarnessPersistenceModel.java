package com.agenttaskmanager.app.harness.state;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import java.util.List;
import java.util.Map;

public record HarnessPersistenceModel(
    long queueDepth,
    Map<String, Object> cachedTaskContext,
    Map<String, Map<String, Object>> cachedValidationSummaries,
    List<RetrievedSemanticContext> relatedContexts,
    Map<String, Object> storeCounts
) {
}
