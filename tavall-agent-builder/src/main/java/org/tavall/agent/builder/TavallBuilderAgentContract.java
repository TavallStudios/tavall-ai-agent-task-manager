package org.tavall.agent.builder;

import java.util.Set;

/** Stable agent-side composition contract for the external Minecraft Builder platform. */
public final class TavallBuilderAgentContract {
    private static final Set<TavallBuilderRole> ROLES = Set.of(
            TavallBuilderRole.PLANNER,
            TavallBuilderRole.TERRAIN,
            TavallBuilderRole.ARCHITECTURE,
            TavallBuilderRole.DETAIL,
            TavallBuilderRole.REPAIR,
            TavallBuilderRole.VISUAL_CRITIC
    );
    private static final Set<TavallBuilderArtifactKind> ARTIFACT_KINDS = Set.of(
            TavallBuilderArtifactKind.BUILD_SPEC,
            TavallBuilderArtifactKind.SPONGE_SCHEMATIC,
            TavallBuilderArtifactKind.REPLAY,
            TavallBuilderArtifactKind.VISUAL_EVIDENCE,
            TavallBuilderArtifactKind.WORLD_BAKE_MANIFEST
    );

    private TavallBuilderAgentContract() {
    }

    public static Set<TavallBuilderRole> roles() {
        return ROLES;
    }

    public static Set<TavallBuilderArtifactKind> artifactKinds() {
        return ARTIFACT_KINDS;
    }
}
