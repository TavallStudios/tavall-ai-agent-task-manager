package org.tavall.ai.runtime;

import java.util.Locale;

/** Executable Tavall AI process identities. Agent roles remain modules, not runtimes. */
public enum TavallAIRuntime {
    NODE_AGENT;

    public static TavallAIRuntime parse(String value) {
        if (value == null || value.isBlank()) {
            return NODE_AGENT;
        }
        return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }
}
