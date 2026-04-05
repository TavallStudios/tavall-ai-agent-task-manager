package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class JavaSymbolPromptRenderer {

  public JavaSymbolRunContext buildRunContext(
      JavaSymbolNeighborhood neighborhood,
      List<String> warnings,
      boolean reflectionAugmented,
      List<String> validationSummaries
  ) {
    if (neighborhood == null || neighborhood.orderedProfiles().isEmpty()) {
      return new JavaSymbolRunContext(
          "skipped",
          "No deterministic Java symbol context was required for this run.",
          "No deterministic Java symbol context was preloaded.",
          List.of(),
          false
      );
    }
    String summary = "Preloaded Java symbol context for "
        + neighborhood.orderedProfiles().size()
        + " class(es); reflectionAugmented="
        + reflectionAugmented
        + ".";
    return new JavaSymbolRunContext(
        "captured",
        summary,
        promptSection(neighborhood, warnings, reflectionAugmented, validationSummaries),
        neighborhood.targetClassNames(),
        reflectionAugmented
    );
  }

  private String promptSection(
      JavaSymbolNeighborhood neighborhood,
      List<String> warnings,
      boolean reflectionAugmented,
      List<String> validationSummaries
  ) {
    StringBuilder builder = new StringBuilder();
    builder.append("Java behavior guard context:\n");
    builder.append("- reflection augmented: ").append(reflectionAugmented).append('\n');
    if (!warnings.isEmpty()) {
      builder.append("- warnings: ").append(String.join("; ", warnings)).append('\n');
    }
    if (validationSummaries != null && !validationSummaries.isEmpty()) {
      builder.append("- recent validation: ").append(String.join(" | ", validationSummaries)).append('\n');
    }
    for (JavaClassProfile profile : neighborhood.orderedProfiles().stream().limit(6).toList()) {
      builder.append("- class ").append(profile.qualifiedName()).append('\n');
      builder.append("  fields: ").append(memberNames(profile.fields().stream().map(JavaFieldProfile::name).toList())).append('\n');
      builder.append("  methods: ").append(memberNames(profile.methods().stream()
          .filter(method -> !method.constructor())
          .map(method -> method.name() + "(" + String.join(", ", method.parameterTypes()) + ")")
          .toList())).append('\n');
      builder.append("  references: ").append(memberNames(profile.referencedTypes())).append('\n');
    }
    builder.append("- keep public and protected signatures, field types, throws, annotations, and inheritance stable unless the task explicitly requires a contract change.");
    return builder.toString().strip();
  }

  private String memberNames(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "<none>";
    }
    return values.stream().limit(6).collect(Collectors.joining(", "));
  }
}
