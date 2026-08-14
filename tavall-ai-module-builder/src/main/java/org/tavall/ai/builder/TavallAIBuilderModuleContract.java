package org.tavall.ai.builder;

import java.util.Set;

/** Stable Tavall AI composition contract for the external Minecraft Builder platform. */
public final class TavallAIBuilderModuleContract {
    private static final Set<TavallAIBuilderRole> ROLES = Set.of(
            TavallAIBuilderRole.PLANNER,
            TavallAIBuilderRole.TERRAIN,
            TavallAIBuilderRole.ARCHITECTURE,
            TavallAIBuilderRole.DETAIL,
            TavallAIBuilderRole.REPAIR,
            TavallAIBuilderRole.VISUAL_CRITIC
    );
    private static final Set<TavallAIBuilderArtifactKind> ARTIFACT_KINDS = Set.of(
            TavallAIBuilderArtifactKind.BUILD_SPEC,
            TavallAIBuilderArtifactKind.SPONGE_SCHEMATIC,
            TavallAIBuilderArtifactKind.REPLAY,
            TavallAIBuilderArtifactKind.VISUAL_EVIDENCE,
            TavallAIBuilderArtifactKind.WORLD_BAKE_MANIFEST
    );

    private TavallAIBuilderModuleContract() {
    }

    public static Set<TavallAIBuilderRole> roles() {
        return ROLES;
    }

    public static Set<TavallAIBuilderArtifactKind> artifactKinds() {
        return ARTIFACT_KINDS;
    }
}
