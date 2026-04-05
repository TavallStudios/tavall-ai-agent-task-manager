package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JavaContractDigestFactory {

  public JavaContractDigest create(JavaClassProfile profile) {
    return new JavaContractDigest(
        profile.qualifiedName(),
        profile.modifiers(),
        profile.annotations(),
        profile.superClass(),
        profile.interfaces(),
        constructors(profile),
        methods(profile),
        fields(profile),
        profile.referencedTypes()
    );
  }

  private List<String> constructors(JavaClassProfile profile) {
    return profile.methods().stream()
        .filter(JavaMethodProfile::constructor)
        .map(this::methodSignature)
        .sorted()
        .toList();
  }

  private List<String> methods(JavaClassProfile profile) {
    return profile.methods().stream()
        .filter(method -> !method.constructor())
        .map(this::methodSignature)
        .sorted()
        .toList();
  }

  private List<String> fields(JavaClassProfile profile) {
    return profile.fields().stream()
        .sorted(Comparator.comparing(JavaFieldProfile::name))
        .map(field -> String.join("|",
            field.name(),
            field.type(),
            String.join(",", field.modifiers()),
            String.join(",", field.annotations())))
        .toList();
  }

  private String methodSignature(JavaMethodProfile method) {
    return String.join("|",
        method.constructor() ? "ctor" : "method",
        method.name(),
        method.returnType(),
        String.join(",", method.parameterTypes()),
        String.join(",", method.modifiers()),
        String.join(",", method.annotations()),
        String.join(",", method.throwsTypes()));
  }
}
