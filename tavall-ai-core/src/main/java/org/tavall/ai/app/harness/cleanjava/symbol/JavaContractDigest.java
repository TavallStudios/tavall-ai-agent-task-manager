package org.tavall.ai.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaContractDigest(
    String qualifiedName,
    List<String> classModifiers,
    List<String> annotations,
    String superClass,
    List<String> interfaces,
    List<String> constructors,
    List<String> methods,
    List<String> fields,
    List<String> referencedTypes
) {
}

