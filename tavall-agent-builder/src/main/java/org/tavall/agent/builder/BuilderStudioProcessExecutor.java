package org.tavall.agent.builder;

import java.nio.file.Path;
import java.util.List;

/** Runtime/Cloud-supplied execution boundary. The Builder agent never owns shell authority. */
@FunctionalInterface
interface BuilderStudioProcessExecutor {
    BuilderStudioSimulationResult execute(
            List<String> command,
            Path workingDirectory,
            BuilderStudioSimulationRequest request
    ) throws Exception;
}
