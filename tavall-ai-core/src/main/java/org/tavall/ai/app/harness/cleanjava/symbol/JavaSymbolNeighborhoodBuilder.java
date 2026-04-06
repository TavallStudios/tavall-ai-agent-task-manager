package org.tavall.ai.app.harness.cleanjava.symbol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class JavaSymbolNeighborhoodBuilder {

  private static final int MAX_CONTEXT_CLASSES = 20;

  public JavaSymbolNeighborhood buildNeighborhood(
      JavaSourceSymbolCatalog catalog,
      List<String> hintSourcePaths,
      String promptText,
      List<String> changedSourcePaths
  ) {
    Map<String, JavaClassProfile> profilesByClassName = catalog.profilesByClassName();
    if (profilesByClassName.isEmpty()) {
      return new JavaSymbolNeighborhood(List.of(), List.of(), List.of(), List.of("No Java classes were available."));
    }
    LinkedHashSet<String> anchorSourcePaths = new LinkedHashSet<>();
    anchorSourcePaths.addAll(normalizePaths(changedSourcePaths));
    anchorSourcePaths.addAll(normalizePaths(hintSourcePaths));
    anchorSourcePaths.addAll(promptMatchedSourcePaths(catalog, promptText));
    if (anchorSourcePaths.isEmpty()) {
      anchorSourcePaths.add(catalog.classesBySourcePath().keySet().stream().sorted().findFirst().orElse(""));
    }
    LinkedHashSet<String> orderedClassNames = new LinkedHashSet<>();
    for (String sourcePath : anchorSourcePaths) {
      orderedClassNames.addAll(catalog.classesBySourcePath().getOrDefault(sourcePath, List.of()));
    }
    List<String> seedClassNames = List.copyOf(orderedClassNames);
    for (String className : seedClassNames) {
      JavaClassProfile profile = profilesByClassName.get(className);
      if (profile == null) {
        continue;
      }
      orderedClassNames.addAll(catalog.classesBySourcePath().getOrDefault(profile.sourcePath(), List.of()));
      orderedClassNames.addAll(profile.referencedTypes());
      orderedClassNames.addAll(reverseReferences(profilesByClassName, className));
      if (orderedClassNames.size() >= MAX_CONTEXT_CLASSES) {
        break;
      }
    }
    List<String> warnings = new ArrayList<>();
    List<JavaClassProfile> orderedProfiles = orderedClassNames.stream()
        .map(profilesByClassName::get)
        .filter(java.util.Objects::nonNull)
        .limit(MAX_CONTEXT_CLASSES)
        .toList();
    if (orderedClassNames.size() > MAX_CONTEXT_CLASSES) {
      warnings.add("Java symbol context was capped at " + MAX_CONTEXT_CLASSES + " classes.");
    }
    return new JavaSymbolNeighborhood(
        orderedProfiles.stream().map(JavaClassProfile::qualifiedName).toList(),
        anchorSourcePaths.stream().filter(path -> !path.isBlank()).toList(),
        orderedProfiles,
        List.copyOf(warnings)
    );
  }

  private List<String> reverseReferences(Map<String, JavaClassProfile> profilesByClassName, String targetClassName) {
    return profilesByClassName.values().stream()
        .filter(profile -> profile.referencedTypes().contains(targetClassName))
        .map(JavaClassProfile::qualifiedName)
        .sorted()
        .toList();
  }

  private List<String> normalizePaths(List<String> paths) {
    if (paths == null) {
      return List.of();
    }
    return paths.stream()
        .map(this::normalize)
        .filter(path -> !path.isBlank())
        .distinct()
        .sorted()
        .toList();
  }

  private List<String> promptMatchedSourcePaths(JavaSourceSymbolCatalog catalog, String promptText) {
    if (promptText == null || promptText.isBlank()) {
      return List.of();
    }
    String normalizedPrompt = promptText.toLowerCase(Locale.ROOT);
    return catalog.profilesByClassName().values().stream()
        .filter(profile -> normalizedPrompt.contains(profile.simpleName().toLowerCase(Locale.ROOT))
            || normalizedPrompt.contains(sourceFileName(profile.sourcePath()).toLowerCase(Locale.ROOT)))
        .map(JavaClassProfile::sourcePath)
        .distinct()
        .sorted()
        .toList();
  }

  private String sourceFileName(String sourcePath) {
    int separatorIndex = sourcePath.lastIndexOf('/');
    return separatorIndex < 0 ? sourcePath : sourcePath.substring(separatorIndex + 1);
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.strip().replace('\\', '/');
  }
}

