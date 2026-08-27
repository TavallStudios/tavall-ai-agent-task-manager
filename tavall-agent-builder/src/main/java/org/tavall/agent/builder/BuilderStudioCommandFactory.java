package org.tavall.agent.builder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Constructs validated Builder Studio CLI arguments without a shell command string. */
public final class BuilderStudioCommandFactory {
    private BuilderStudioCommandFactory() {
    }

    public static List<String> arguments(BuilderStudioSimulationRequest request) {
        Path workspace = request.workspaceRoot();
        Path artifact = requireInside(workspace, request.artifactPath(), "artifactPath");
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add("--open");
        arguments.add(artifact.toString());
        arguments.add("--builder-job");
        arguments.add(request.jobId());
        arguments.add("--mode");
        arguments.add(request.mode().cliValue());
        request.worldId().ifPresent(world -> {
            arguments.add("--world");
            arguments.add(world);
        });
        arguments.add("--speed");
        arguments.add(formatSpeed(request.playbackSpeed()));
        if (request.autoplay()) {
            arguments.add("--autoplay");
        }
        if (request.initialTick().isPresent()) {
            arguments.add("--initial-tick");
            arguments.add(Long.toString(request.initialTick().getAsLong()));
        }
        if (request.finalTick().isPresent()) {
            arguments.add("--final-tick");
            arguments.add(Long.toString(request.finalTick().getAsLong()));
        }
        if (request.visibleEntityCap().isPresent()) {
            arguments.add("--visible-entity-cap");
            arguments.add(Integer.toString(request.visibleEntityCap().getAsInt()));
        }
        request.evidenceDirectory().ifPresent(path -> {
            arguments.add("--evidence-dir");
            arguments.add(requireInside(workspace, path, "evidenceDirectory").toString());
        });
        return List.copyOf(arguments);
    }

    private static Path requireInside(Path workspace, Path path, String fieldName) {
        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedWorkspace)) {
            throw new IllegalArgumentException(fieldName + " must remain inside the authorized workspace");
        }
        return normalizedPath;
    }

    private static String formatSpeed(double speed) {
        if (speed == Math.rint(speed)) {
            return Long.toString((long) speed);
        }
        return String.format(Locale.ROOT, "%s", speed);
    }
}
