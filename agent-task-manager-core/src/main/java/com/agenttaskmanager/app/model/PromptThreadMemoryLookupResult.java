package com.agenttaskmanager.app.model;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import java.util.List;

public record PromptThreadMemoryLookupResult(
    String threadKey,
    PromptThreadSummary exactThread,
    String summary,
    String section,
    List<RetrievedSemanticContext> threadContexts,
    List<RetrievedSemanticContext> projectContexts,
    List<RetrievedSemanticContext> knowledgeContexts
) {
}
