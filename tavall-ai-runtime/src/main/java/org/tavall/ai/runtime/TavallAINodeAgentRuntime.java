package org.tavall.ai.runtime;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.bootstrap.TavallAIModule;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

final class TavallAINodeAgentRuntime {
    private TavallAINodeAgentRuntime() {
    }

    static int run(List<String> arguments, PrintStream output, ClassLoader classLoader) throws Exception {
        TavallAIRuntimeContext context = TavallAIRuntimeContext.load(classLoader);
        requireInstalledComposition(context, "Node Agent");

        if (arguments.contains("--describe")) {
            describe(context, output);
            return 0;
        }

        List<TavallAINodeAgentHost> hosts = new ArrayList<>();
        ServiceLoader.load(TavallAINodeAgentHost.class, classLoader).forEach(hosts::add);
        if (hosts.size() != 1) {
            throw new IllegalStateException(
                    "Tavall AI Node Agent requires exactly one authorized host adapter; found " + hosts.size()
            );
        }
        return hosts.getFirst().run(context, List.copyOf(arguments), output);
    }

    private static void requireInstalledComposition(TavallAIRuntimeContext context, String runtimeName) {
        if (context.roles().size() == 0) {
            throw new IllegalStateException("Tavall AI " + runtimeName + " has no installed role modules");
        }
        if (context.modules().size() == 0) {
            throw new IllegalStateException("Tavall AI " + runtimeName + " has no installed capability/domain modules");
        }
    }

    private static void describe(TavallAIRuntimeContext context, PrintStream output) {
        output.println("runtime=NODE_AGENT");
        output.println("roles=" + context.roles().size());
        context.roles().roles().stream()
                .sorted(Comparator.comparing(TavallAIAgentRole::id))
                .forEach(role -> output.println("role=" + role.id()));
        output.println("modules=" + context.modules().size());
        context.modules().modules().stream()
                .sorted(Comparator.comparing(TavallAIModule::id))
                .forEach(module -> output.println("module=" + module.id()));
    }
}
