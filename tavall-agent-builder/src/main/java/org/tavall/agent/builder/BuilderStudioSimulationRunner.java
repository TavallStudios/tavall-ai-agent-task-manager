package org.tavall.agent.builder;

/** Executes one Builder Studio simulation through an authority-supplied process boundary. */
@FunctionalInterface
public interface BuilderStudioSimulationRunner {
    BuilderStudioSimulationResult run(BuilderStudioSimulationRequest request) throws Exception;
}
