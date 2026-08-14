package org.tavall.ai.builder;

import org.tavall.ai.bootstrap.TavallAIModule;
import org.tavall.ai.bootstrap.TavallAIModuleProvider;
import org.tavall.ai.execution.distributed.DistributedExecutionModuleProvider;

import java.util.Set;

/** Tavall AI domain module descriptor for the existing Minecraft Builder platform. */
public final class BuilderModuleProvider implements TavallAIModuleProvider {
    public static final String MODULE_ID = "builder";

    @Override
    public TavallAIModule module() {
        return new TavallAIModule(
                MODULE_ID,
                "Composes Builder planning, repair, visual critique, skills, and artifact evidence "
                        + "without duplicating minecraft-bot-builder implementation.",
                Set.of(DistributedExecutionModuleProvider.MODULE_ID)
        );
    }
}
