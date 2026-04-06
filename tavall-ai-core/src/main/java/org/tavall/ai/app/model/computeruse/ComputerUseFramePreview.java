package org.tavall.ai.app.model.computeruse;

import java.util.Map;

public record ComputerUseFramePreview(
    String captureMode,
    String outputPath,
    String base64Png,
    Map<String, Object> bounds
) {
}

