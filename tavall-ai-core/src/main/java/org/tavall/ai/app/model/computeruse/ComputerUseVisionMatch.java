package org.tavall.ai.app.model.computeruse;

import java.util.Map;

public record ComputerUseVisionMatch(
    boolean matched,
    double score,
    String templatePath,
    Map<String, Object> bounds,
    ComputerUseSessionArtifact artifact
) {
}

