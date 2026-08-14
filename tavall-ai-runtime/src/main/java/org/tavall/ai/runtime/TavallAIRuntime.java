package org.tavall.ai.runtime;

import java.util.Locale;

/** Executable Tavall AI process identities. Role/domain capabilities remain modules, not runtimes. */
public enum TavallAIRuntime {
    NODE_AGENT,
    CHATGPT_WEB;

    public static TavallAIRuntime parse(String value) {
        if (value == null || value.isBlank()) {
            return NODE_AGENT;
        }
        return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }
}
