package org.tavall.ai.execution.distributed;

import org.tavall.ai.bootstrap.TavallAIModule;
import org.tavall.ai.bootstrap.TavallAIModuleProvider;

import java.util.Set;

/** Bootstrap descriptor for the Tavall AI distributed execution capability module. */
public final class DistributedExecutionModuleProvider implements TavallAIModuleProvider {
    public static final String MODULE_ID = "distributed-execution";

    @Override
    public TavallAIModule module() {
        return new TavallAIModule(
                MODULE_ID,
                "Routes bounded AI calls across already-authorized node and web execution surfaces.",
                Set.of()
        );
    }
}
