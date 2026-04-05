package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JavaSymbolSemanticDocumentRenderer {

  public String render(JavaClassProfile profile) {
    StringBuilder builder = new StringBuilder();
    builder.append("Java symbol profile").append('\n');
    builder.append("Class: ").append(profile.qualifiedName()).append('\n');
    builder.append("Package: ").append(profile.packageName()).append('\n');
    builder.append("Source: ").append(profile.sourcePath()).append('\n');
    builder.append("Modifiers: ").append(join(profile.modifiers())).append('\n');
    builder.append("Annotations: ").append(join(profile.annotations())).append('\n');
    builder.append("Superclass: ").append(blank(profile.superClass())).append('\n');
    builder.append("Interfaces: ").append(join(profile.interfaces())).append('\n');
    builder.append("Fields: ").append(renderFields(profile.fields())).append('\n');
    builder.append("Methods: ").append(renderMethods(profile.methods())).append('\n');
    builder.append("Nested types: ").append(join(profile.nestedTypes())).append('\n');
    builder.append("References: ").append(join(profile.referencedTypes())).append('\n');
    if (!profile.comments().isEmpty()) {
      builder.append("Comments: ").append(join(profile.comments())).append('\n');
    }
    builder.append("Behavior note: preserve public and protected signatures, field types, throws, inheritance, and annotations unless the task explicitly changes the contract.");
    return builder.toString().strip();
  }

  private String renderFields(List<JavaFieldProfile> fields) {
    if (fields == null || fields.isEmpty()) {
      return "<none>";
    }
    return fields.stream()
        .limit(12)
        .map(field -> join(field.modifiers()) + " " + blank(field.type()) + " " + field.name())
        .map(String::strip)
        .collect(Collectors.joining("; "));
  }

  private String renderMethods(List<JavaMethodProfile> methods) {
    if (methods == null || methods.isEmpty()) {
      return "<none>";
    }
    return methods.stream()
        .limit(18)
        .map(this::signature)
        .collect(Collectors.joining("; "));
  }

  private String signature(JavaMethodProfile method) {
    String parameters = String.join(", ", method.parameterTypes());
    String throwsClause = method.throwsTypes().isEmpty() ? "" : " throws " + String.join(", ", method.throwsTypes());
    if (method.constructor()) {
      return join(method.modifiers()) + " " + method.name() + "(" + parameters + ")" + throwsClause;
    }
    return join(method.modifiers()) + " " + blank(method.returnType()) + " " + method.name() + "(" + parameters + ")" + throwsClause;
  }

  private String join(List<String> values) {
    return values == null || values.isEmpty() ? "<none>" : String.join(", ", values);
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? "<none>" : value.strip();
  }
}
