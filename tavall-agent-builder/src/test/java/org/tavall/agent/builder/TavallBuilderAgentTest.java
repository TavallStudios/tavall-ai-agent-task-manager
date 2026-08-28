package org.tavall.agent.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallBuilderAgentTest {
    @Test
    void builderIsAnAgentThatRequiresDistributedExecutionRuntimeModule() {
        TavallAgent agent = new BuilderAgentProvider().agent();

        assertEquals("builder", agent.id());
        assertEquals(Set.of("distributed-execution"), agent.requiredRuntimeModuleIds());
        assertTrue(agent.capabilities().contains(TavallAgentCapability.FUNCTION_DISCOVERY));
        assertEquals(Set.of(
                TavallBuilderRole.CONCEPT,
                TavallBuilderRole.PLANNER,
                TavallBuilderRole.TERRAIN,
                TavallBuilderRole.ARCHITECTURE,
                TavallBuilderRole.DETAIL,
                TavallBuilderRole.REPAIR,
                TavallBuilderRole.VISUAL_CRITIC
        ), TavallBuilderAgentContract.roles());
        assertEquals(Set.of(
                TavallBuilderArtifactKind.BUILD_SPEC,
                TavallBuilderArtifactKind.SPONGE_SCHEMATIC,
                TavallBuilderArtifactKind.REPLAY,
                TavallBuilderArtifactKind.VISUAL_EVIDENCE,
                TavallBuilderArtifactKind.WORLD_BAKE_MANIFEST
        ), TavallBuilderAgentContract.artifactKinds());
        assertTrue(agent.instructions().contains("prepareFirstConcept"));
        assertTrue(agent.instructions().contains("acceptFirstConcept"));
        assertTrue(agent.instructions().contains("MineBench"));
    }

    @Test
    void studioCommandIsTypedAndWorkspaceBound(@TempDir Path workspace) {
        BuilderStudioSimulationRequest request = request(workspace);
        Path replay = request.artifactPath();
        Path evidence = request.evidenceDirectory().orElseThrow();

        assertEquals(
                List.of(
                        "--open", replay.toAbsolutePath().normalize().toString(),
                        "--builder-job", "builder-job-7",
                        "--mode", "visible",
                        "--world", "ffa",
                        "--speed", "16",
                        "--autoplay",
                        "--initial-tick", "100",
                        "--final-tick", "800",
                        "--visible-entity-cap", "50",
                        "--evidence-dir", evidence.toAbsolutePath().normalize().toString()
                ),
                BuilderStudioCommandFactory.arguments(request)
        );
    }

    @Test
    void processRunnerAppendsTypedArgumentsToTrustedLauncher(@TempDir Path workspace) throws Exception {
        BuilderStudioSimulationRequest request = request(workspace);
        AtomicReference<List<String>> observedCommand = new AtomicReference<>();
        AtomicReference<Path> observedDirectory = new AtomicReference<>();
        ProcessBuilderStudioSimulationRunner runner = new ProcessBuilderStudioSimulationRunner(
                List.of("npm", "--prefix", "minecraft-bot-builder/studio-app", "start", "--"),
                (command, workingDirectory, simulationRequest) -> {
                    observedCommand.set(command);
                    observedDirectory.set(workingDirectory);
                    return new BuilderStudioSimulationResult(
                            "studio-session-7",
                            BuilderStudioSimulationStatus.STARTED,
                            simulationRequest.artifactPath(),
                            List.of(),
                            ""
                    );
                }
        );

        BuilderStudioSimulationResult result = runner.run(request);

        assertEquals(BuilderStudioSimulationStatus.STARTED, result.status());
        assertEquals(workspace.toAbsolutePath().normalize(), observedDirectory.get());
        assertEquals("npm", observedCommand.get().getFirst());
        assertTrue(observedCommand.get().contains("--open"));
        assertTrue(observedCommand.get().contains(request.artifactPath().toAbsolutePath().normalize().toString()));
    }

    @Test
    void studioCommandRejectsPathsOutsideWorkspace(@TempDir Path workspace) {
        BuilderStudioSimulationRequest request = new BuilderStudioSimulationRequest(
                "builder-job-8",
                workspace,
                workspace.getParent().resolve("outside.replay.json"),
                Optional.empty(),
                1.0,
                false,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                BuilderStudioSimulationMode.EVIDENCE
        );

        assertThrows(IllegalArgumentException.class, () -> BuilderStudioCommandFactory.arguments(request));
    }

    @Test
    void studioCommandRejectsUnsupportedSpeedAndReversedTicks(@TempDir Path workspace) {
        Path replay = workspace.resolve("build.replay.json");
        assertThrows(IllegalArgumentException.class, () -> new BuilderStudioSimulationRequest(
                "builder-job-9",
                workspace,
                replay,
                Optional.empty(),
                2.0,
                false,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                BuilderStudioSimulationMode.EVIDENCE
        ));
        assertThrows(IllegalArgumentException.class, () -> new BuilderStudioSimulationRequest(
                "builder-job-10",
                workspace,
                replay,
                Optional.empty(),
                4.0,
                false,
                OptionalLong.of(20),
                OptionalLong.of(10),
                OptionalInt.empty(),
                Optional.empty(),
                BuilderStudioSimulationMode.EVIDENCE
        ));
    }

    @Test
    void builderAgentContractContainsNoAiNamedTypes() {
        assertTrue(List.of(
                BuilderAgentProvider.class,
                TavallBuilderAgentContract.class,
                TavallBuilderRole.class,
                TavallBuilderArtifactKind.class,
                BuilderStudioSimulationRequest.class,
                BuilderStudioSimulationRunner.class
        ).stream().noneMatch(type -> type.getSimpleName().contains("AI")));
    }

    private BuilderStudioSimulationRequest request(Path workspace) {
        return new BuilderStudioSimulationRequest(
                "builder-job-7",
                workspace,
                workspace.resolve("artifacts/build.replay.json"),
                Optional.of("ffa"),
                16.0,
                true,
                OptionalLong.of(100),
                OptionalLong.of(800),
                OptionalInt.of(50),
                Optional.of(workspace.resolve("evidence")),
                BuilderStudioSimulationMode.VISIBLE
        );
    }
}
