package org.tavall.ai.runtime;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Canonical executable entry point for Tavall AI process runtimes. */
public final class TavallAIRuntimeMain {
    public static final String RUNTIME_ENVIRONMENT = "TAVALL_AI_RUNTIME";

    private TavallAIRuntimeMain() {
    }

    public static void main(String[] args) throws Exception {
        int exitCode = run(args, System.getenv(), System.out, Thread.currentThread().getContextClassLoader());
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, Map<String, String> environment, PrintStream output, ClassLoader classLoader)
            throws Exception {
        String[] safeArgs = args == null ? new String[0] : args;
        String runtimeValue = safeArgs.length > 0 && !safeArgs[0].startsWith("--")
                ? safeArgs[0]
                : environment.get(RUNTIME_ENVIRONMENT);
        TavallAIRuntime runtime = TavallAIRuntime.parse(runtimeValue);
        int argumentOffset = safeArgs.length > 0 && !safeArgs[0].startsWith("--") ? 1 : 0;
        List<String> runtimeArguments = List.copyOf(Arrays.asList(safeArgs).subList(argumentOffset, safeArgs.length));

        return switch (runtime) {
            case NODE_AGENT -> TavallAINodeAgentRuntime.run(runtimeArguments, output, classLoader);
            case CHATGPT_WEB -> TavallAIChatGPTWebRuntime.run(runtimeArguments, output, classLoader);
        };
    }
}
