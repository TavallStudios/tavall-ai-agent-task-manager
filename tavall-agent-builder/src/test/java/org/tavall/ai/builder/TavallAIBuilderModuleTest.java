package org.tavall.ai.builder;

import org.junit.jupiter.api.Test;
import org.tavall.ai.bootstrap.TavallAIModule;
import org.tavall.ai.execution.distributed.DistributedExecutionModuleProvider;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallAIBuilderModuleTest {
    @Test
    void builderDeclaresExistingDomainRolesWithoutDuplicatingBuilderImplementation() {
        assertEquals(Set.of(
                TavallAIBuilderRole.PLANNER,
                TavallAIBuilderRole.TERRAIN,
                TavallAIBuilderRole.ARCHITECTURE,
                TavallAIBuilderRole.DETAIL,
                TavallAIBuilderRole.REPAIR,
                TavallAIBuilderRole.VISUAL_CRITIC
        ), TavallAIBuilderModuleContract.roles());

        assertEquals(Set.of(
                TavallAIBuilderArtifactKind.BUILD_SPEC,
                TavallAIBuilderArtifactKind.SPONGE_SCHEMATIC,
                TavallAIBuilderArtifactKind.REPLAY,
                TavallAIBuilderArtifactKind.VISUAL_EVIDENCE,
                TavallAIBuilderArtifactKind.WORLD_BAKE_MANIFEST
        ), TavallAIBuilderModuleContract.artifactKinds());
    }

    @Test
    void builderDependsOnDistributedExecutionAsADomainModule() {
        TavallAIModule module = new BuilderModuleProvider().module();

        assertEquals("builder", module.id());
        assertTrue(module.requiredModuleIds().contains(DistributedExecutionModuleProvider.MODULE_ID));
    }
}
