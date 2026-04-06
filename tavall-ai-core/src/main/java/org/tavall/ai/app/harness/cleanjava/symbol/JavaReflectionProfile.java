package org.tavall.ai.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaReflectionProfile(
    String qualifiedName,
    List<String> runtimeAnnotations,
    List<String> declaredFields,
    List<String> declaredConstructors,
    List<String> declaredMethods,
    String superClass,
    List<String> interfaces
) {
}

