package org.tavall.ai.runtime;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentRegistry;
import org.tavall.ai.bootstrap.TavallAIModule;
import org.tavall.ai.bootstrap.TavallAIModuleRegistry;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Runtime bootstrap result shared with an authorized Tavall AI host adapter. */
public record TavallAIRuntimeContext(
        TavallAgentRegistry agents,
        TavallAIModuleRegistry modules
) {
    public TavallAIRuntimeContext {
        agents = Objects.requireNonNull(agents, "agents");
        modules = Objects.requireNonNull(modules, "modules");
        validateRuntimeRequirements(agents, modules);
    }

    public static TavallAIRuntimeContext load(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        return new TavallAIRuntimeContext(
                TavallAgentRegistry.load(classLoader),
                TavallAIModuleRegistry.load(classLoader)
        );
    }

    private static void validateRuntimeRequirements(
            TavallAgentRegistry agents,
            TavallAIModuleRegistry modules
    ) {
        Set<String> installedModuleIds = modules.modules().stream()
                .map(TavallAIModule::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (TavallAgent agent : agents.agents()) {
            LinkedHashSet<String> missing = new LinkedHashSet<>(agent.requiredRuntimeModuleIds());
            missing.removeAll(installedModuleIds);
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "Tavall agent " + agent.id() + " requires missing runtime modules " + missing
                );
            }
        }
    }
}
