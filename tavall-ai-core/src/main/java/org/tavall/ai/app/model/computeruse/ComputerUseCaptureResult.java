package org.tavall.ai.app.model.computeruse;

public record ComputerUseCaptureResult(
    String captureMode,
    String outputPath,
    ComputerUseSessionArtifact artifact
) {
}

