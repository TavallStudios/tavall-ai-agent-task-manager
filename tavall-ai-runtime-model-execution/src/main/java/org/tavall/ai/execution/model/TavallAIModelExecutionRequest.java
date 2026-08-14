package org.tavall.ai.execution.model;

import org.tavall.ai.core.catalog.AIFunctionCatalogView;

import java.util.Objects;

/** Immutable request handed to an actual AI/model provider adapter. */
public record TavallAIModelExecutionRequest(
        TavallAIModelExecutionDefinition definition,
        TavallAIModelJob job,
        TavallAIModelExecutionBudget budget,
        AIFunctionCatalogView functionView
) {
    public TavallAIModelExecutionRequest {
        definition = Objects.requireNonNull(definition, "definition");
        job = Objects.requireNonNull(job, "job");
        budget = Objects.requireNonNull(budget, "budget");
        functionView = Objects.requireNonNull(functionView, "functionView");
    }
}
