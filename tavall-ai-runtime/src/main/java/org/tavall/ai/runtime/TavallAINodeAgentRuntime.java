package org.tavall.ai.runtime;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleRegistry;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

final class TavallAINodeAgentRuntime {
    private TavallAINodeAgentRuntime() {
    }

    static int run(List<String> arguments, PrintStream output, ClassLoader classLoader) throws Exception {
        TavallAIAgentRoleRegistry roles = TavallAIAgentRoleRegistry.load(classLoader);
        if (roles.size() == 0) {
            throw new IllegalStateException("Tavall AI Node Agent has no installed role modules");
        }

        if (arguments.contains("--describe")) {
            describe(roles, output);
            return 0;
        }

        List<TavallAINodeAgentHost> hosts = new ArrayList<>();
        ServiceLoader.load(TavallAINodeAgentHost.class, classLoader).forEach(hosts::add);
        if (hosts.size() != 1) {
            throw new IllegalStateException(
                    "Tavall AI Node Agent requires exactly one authorized host adapter; found " + hosts.size()
            );
        }
        return hosts.getFirst().run(roles, List.copyOf(arguments), output);
    }

    private static void describe(TavallAIAgentRoleRegistry roles, PrintStream output) {
        output.println("runtime=NODE_AGENT");
        output.println("roles=" + roles.size());
        roles.roles().stream()
                .sorted(Comparator.comparing(TavallAIAgentRole::id))
                .forEach(role -> output.println("role=" + role.id()));
    }
}
