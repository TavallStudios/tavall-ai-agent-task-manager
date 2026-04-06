package org.tavall.ai.app.model.bridge;

import java.util.List;

public record BridgeAutomationSessionCapabilities(
    String sessionId,
    String agentId,
    String repoPath,
    String bridgeTarget,
    String transport,
    boolean cooperativeAutomation,
    boolean intrusiveInput,
    List<String> supportedCommands
) {
}

