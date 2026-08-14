package org.tavall.agent.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agent.TavallAgent;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallBuilderAgentTest {
    @Test
    void builderIsAnAgentThatRequiresDistributedExecutionRuntimeModule() {
        TavallAgent agent = new BuilderAgentProvider().agent();

        assertEquals("builder", agent.id());
        assertEquals(Set.of("distributed-execution"), agent.requiredRuntimeModuleIds());
        assertEquals(Set.of(
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
    }

    @Test
    void studioCommandIsTypedAndWorkspaceBound(@TempDir Path workspace) throws Exception {
        Path replay = workspace.resolve("artifacts/build.replay.json");
        Path evidence = workspace.resolve("evidence");
        BuilderStudioSimulationRequest request = new BuilderStudioSimulationRequest(
                "builder-job-7",
                workspace,
                replay,
                Optional.of("ffa"),
                16.0,
                true,
                OptionalLong.of(100),
                OptionalLong.of(800),
                OptionalInt.of(50),
                Optional.of(evidence),
                BuilderStudioSimulationMode.VISIBLE
        );

        assertEquals(
                java.util.List.of(
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
        assertTrue(java.util.List.of(
                BuilderAgentProvider.class,
                TavallBuilderAgentContract.class,
                TavallBuilderRole.class,
                TavallBuilderArtifactKind.class,
                BuilderStudioSimulationRequest.class
        ).stream().noneMatch(type -> type.getSimpleName().contains("AI")));
    }
}
