package org.tavall.agent.builder;

import java.nio.file.Path;
import java.util.List;

/** Result/evidence references returned by an authorized Studio launch boundary. */
public record BuilderStudioSimulationResult(
        String sessionId,
        BuilderStudioSimulationStatus status,
        Path artifactPath,
        List<Path> evidenceReferences,
        String diagnostics
) {
    public BuilderStudioSimulationResult {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        sessionId = sessionId.trim();
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        artifactPath = artifactPath.toAbsolutePath().normalize();
        evidenceReferences = evidenceReferences == null
                ? List.of()
                : evidenceReferences.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        diagnostics = diagnostics == null ? "" : diagnostics;
    }
}
