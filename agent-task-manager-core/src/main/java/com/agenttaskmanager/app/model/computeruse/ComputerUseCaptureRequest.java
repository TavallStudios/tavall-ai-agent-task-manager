package com.agenttaskmanager.app.model.computeruse;

public record ComputerUseCaptureRequest(
    String sessionId,
    String titleContains,
    String processName,
    boolean storeArtifact,
    String artifactKind,
    String summary
) {
}
