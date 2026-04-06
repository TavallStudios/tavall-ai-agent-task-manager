package org.tavall.ai.app.model.computeruse;

import java.util.List;
import java.util.Map;

public record ComputerUseScenarioStep(
    String stepId,
    String title,
    String objective,
    List<String> markers
) {

  public Map<String, Object> toMetadata() {
    return Map.of(
        "stepId", stepId,
        "title", title,
        "objective", objective,
        "markers", markers
    );
  }
}

