package com.agenttaskmanager.app.model.bridge;

import java.util.List;

public record BridgeAutomationCommandDefinition(
    String commandId,
    String displayName,
    String description,
    String isolationClass,
    boolean intrusiveInput,
    boolean requiresForeground,
    List<BridgeAutomationParameterDefinition> parameters
) {
}
