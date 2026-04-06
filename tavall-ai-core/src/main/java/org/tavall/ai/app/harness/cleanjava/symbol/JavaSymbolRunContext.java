package org.tavall.ai.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaSymbolRunContext(
    String status,
    String summary,
    String promptSection,
    List<String> targetedClasses,
    boolean reflectionAugmented
) {
}

