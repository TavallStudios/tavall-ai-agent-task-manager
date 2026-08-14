package org.tavall.agent.builder;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/** Typed request for one authorized Builder Studio simulation. */
public record BuilderStudioSimulationRequest(
        String jobId,
        Path workspaceRoot,
        Path artifactPath,
        Optional<String> worldId,
        double playbackSpeed,
        boolean autoplay,
        OptionalLong initialTick,
        OptionalLong finalTick,
        OptionalInt visibleEntityCap,
        Optional<Path> evidenceDirectory,
        BuilderStudioSimulationMode mode
) {
    private static final Set<Double> ALLOWED_SPEEDS = Set.of(0.25, 1.0, 4.0, 16.0, 64.0);

    public BuilderStudioSimulationRequest {
        jobId = requireText(jobId, "jobId");
        workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        artifactPath = Objects.requireNonNull(artifactPath, "artifactPath").toAbsolutePath().normalize();
        worldId = worldId == null ? Optional.empty() : worldId.map(value -> requireText(value, "worldId"));
        if (!ALLOWED_SPEEDS.contains(playbackSpeed)) {
            throw new IllegalArgumentException("Unsupported Builder Studio playbackSpeed: " + playbackSpeed);
        }
        initialTick = initialTick == null ? OptionalLong.empty() : initialTick;
        finalTick = finalTick == null ? OptionalLong.empty() : finalTick;
        if (initialTick.isPresent() && initialTick.getAsLong() < 0) {
            throw new IllegalArgumentException("initialTick must be non-negative");
        }
        if (finalTick.isPresent() && finalTick.getAsLong() < 0) {
            throw new IllegalArgumentException("finalTick must be non-negative");
        }
        if (initialTick.isPresent() && finalTick.isPresent()
                && finalTick.getAsLong() < initialTick.getAsLong()) {
            throw new IllegalArgumentException("finalTick must be greater than or equal to initialTick");
        }
        visibleEntityCap = visibleEntityCap == null ? OptionalInt.empty() : visibleEntityCap;
        if (visibleEntityCap.isPresent() && visibleEntityCap.getAsInt() < 1) {
            throw new IllegalArgumentException("visibleEntityCap must be positive");
        }
        evidenceDirectory = evidenceDirectory == null
                ? Optional.empty()
                : evidenceDirectory.map(path -> path.toAbsolutePath().normalize());
        mode = Objects.requireNonNull(mode, "mode");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
