package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaClassProfile(
    String qualifiedName,
    String simpleName,
    String packageName,
    String sourcePath,
    List<String> modifiers,
    List<String> annotations,
    String superClass,
    List<String> interfaces,
    List<String> imports,
    List<JavaFieldProfile> fields,
    List<JavaMethodProfile> methods,
    List<String> nestedTypes,
    List<String> referencedTypes,
    List<String> comments
) {
}
