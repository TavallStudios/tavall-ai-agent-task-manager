package org.tavall.ai.execution.model;

import org.tavall.ai.core.catalog.AIFunctionCatalog;
import org.tavall.ai.core.catalog.AIFunctionCatalogView;

/** Resolves the authoritative Function Catalog policy view for one model execution. */
@FunctionalInterface
public interface TavallAIModelFunctionViewResolver {
    AIFunctionCatalogView resolve(
            AIFunctionCatalog catalog,
            TavallAIModelExecutionDefinition definition,
            TavallAIModelJob job
    );
}
