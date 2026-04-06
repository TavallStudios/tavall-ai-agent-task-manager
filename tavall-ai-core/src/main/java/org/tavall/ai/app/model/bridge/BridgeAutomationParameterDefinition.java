package org.tavall.ai.app.model.bridge;

public record BridgeAutomationParameterDefinition(
    String name,
    String valueType,
    boolean required,
    String description
) {
}

