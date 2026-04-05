package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaSymbolNeighborhood(
    List<String> targetClassNames,
    List<String> anchorSourcePaths,
    List<JavaClassProfile> orderedProfiles,
    List<String> warnings
) {
}
