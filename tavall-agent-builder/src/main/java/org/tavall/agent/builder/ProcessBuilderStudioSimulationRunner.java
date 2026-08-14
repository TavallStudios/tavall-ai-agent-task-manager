package org.tavall.agent.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Appends validated Studio arguments to a trusted launcher and delegates execution to the runtime. */
public final class ProcessBuilderStudioSimulationRunner implements BuilderStudioSimulationRunner {
    private final List<String> launcherCommand;
    private final BuilderStudioProcessExecutor processExecutor;

    ProcessBuilderStudioSimulationRunner(
            List<String> launcherCommand,
            BuilderStudioProcessExecutor processExecutor
    ) {
        if (launcherCommand == null || launcherCommand.isEmpty()) {
            throw new IllegalArgumentException("launcherCommand must not be empty");
        }
        this.launcherCommand = launcherCommand.stream().map(ProcessBuilderStudioSimulationRunner::requireText).toList();
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
    }

    @Override
    public BuilderStudioSimulationResult run(BuilderStudioSimulationRequest request) throws Exception {
        ArrayList<String> command = new ArrayList<>(launcherCommand);
        command.addAll(BuilderStudioCommandFactory.arguments(Objects.requireNonNull(request, "request")));
        return processExecutor.execute(
                List.copyOf(command),
                request.workspaceRoot().toAbsolutePath().normalize(),
                request
        );
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("launcher command entries must not be blank");
        }
        return value;
    }
}
