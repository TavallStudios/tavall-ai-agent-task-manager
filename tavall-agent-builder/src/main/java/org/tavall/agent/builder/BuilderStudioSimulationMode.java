package org.tavall.agent.builder;

import java.util.Locale;

public enum BuilderStudioSimulationMode {
    VISIBLE,
    EVIDENCE;

    public String cliValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
