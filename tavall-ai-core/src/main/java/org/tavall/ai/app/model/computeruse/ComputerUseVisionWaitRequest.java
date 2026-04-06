package org.tavall.ai.app.model.computeruse;

public record ComputerUseVisionWaitRequest(
    String sessionId,
    String anchorKey,
    String templatePath,
    String titleContains,
    String processName,
    double threshold,
    int timeoutMs,
    boolean storeArtifact
) {
}

