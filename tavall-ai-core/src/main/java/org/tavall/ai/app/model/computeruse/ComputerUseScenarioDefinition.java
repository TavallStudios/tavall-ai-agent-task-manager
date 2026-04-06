package org.tavall.ai.app.model.computeruse;

import java.util.List;
import java.util.Map;

public record ComputerUseScenarioDefinition(
    String scenarioId,
    String description,
    List<ComputerUseScenarioStep> steps,
    List<String> expectedArtifacts,
    List<String> passFailGates,
    Map<String, Object> artifactPolicy
) {

  public Map<String, Object> toMetadata() {
    return Map.of(
        "scenarioId", scenarioId,
        "description", description,
        "steps", steps.stream().map(ComputerUseScenarioStep::toMetadata).toList(),
        "expectedArtifacts", expectedArtifacts,
        "passFailGates", passFailGates,
        "artifactPolicy", artifactPolicy
    );
  }
}

