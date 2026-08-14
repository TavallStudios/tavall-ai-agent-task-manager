package org.tavall.ai.execution.model;

/** Adapter for one actual AI/model execution backend. */
public interface TavallAIModelProvider {
    String providerId();

    TavallAIModelExecutionResult execute(TavallAIModelExecutionRequest request) throws Exception;
}
