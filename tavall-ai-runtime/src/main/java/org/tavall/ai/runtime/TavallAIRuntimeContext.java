package org.tavall.ai.runtime;

import org.tavall.ai.agent.role.TavallAIAgentRoleRegistry;
import org.tavall.ai.bootstrap.TavallAIModuleRegistry;

import java.util.Objects;

/** Runtime bootstrap result shared with an authorized Tavall AI host adapter. */
public record TavallAIRuntimeContext(
        TavallAIAgentRoleRegistry roles,
        TavallAIModuleRegistry modules
) {
    public TavallAIRuntimeContext {
        roles = Objects.requireNonNull(roles, "roles");
        modules = Objects.requireNonNull(modules, "modules");
    }

    public static TavallAIRuntimeContext load(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        return new TavallAIRuntimeContext(
                TavallAIAgentRoleRegistry.load(classLoader),
                TavallAIModuleRegistry.load(classLoader)
        );
    }
}
