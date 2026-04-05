package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaMethodProfile(
    String name,
    String returnType,
    List<String> parameterTypes,
    List<String> parameterNames,
    List<String> modifiers,
    List<String> annotations,
    List<String> throwsTypes,
    List<JavaLocalVariableProfile> localVariables,
    String comment,
    boolean constructor
) {
}
