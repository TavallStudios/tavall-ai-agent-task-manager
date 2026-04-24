package org.tavall.ai.app.harness.state;

import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
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

