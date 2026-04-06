package org.tavall.ai.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaReflectionAugmentationResult(
    boolean augmented,
    List<JavaReflectionProfile> profiles,
    List<String> warnings
) {
}

