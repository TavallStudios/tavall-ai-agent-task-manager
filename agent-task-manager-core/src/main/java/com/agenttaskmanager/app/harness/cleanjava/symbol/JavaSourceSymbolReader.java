package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;

@Service
public class JavaSourceSymbolReader {

  private final JavaClassProfileFactory profileFactory = new JavaClassProfileFactory();
  private final JavaSourceFileDiscoveryService sourceFileDiscoveryService;

  public JavaSourceSymbolReader(JavaSourceFileDiscoveryService sourceFileDiscoveryService) {
    this.sourceFileDiscoveryService = sourceFileDiscoveryService;
  }

  public JavaSourceSymbolCatalog readCatalog(Path repoRoot) {
    List<String> javaSourcePaths = sourceFileDiscoveryService.listJavaSourcePaths(repoRoot);
    if (javaSourcePaths.isEmpty()) {
      return new JavaSourceSymbolCatalog(Map.of(), Map.of());
    }
    CtModel model = buildModel(repoRoot, javaSourcePaths);
    Set<String> projectTypeNames = model.getAllTypes().stream()
        .map(CtType::getQualifiedName)
        .filter(name -> name != null && !name.isBlank())
        .collect(java.util.stream.Collectors.toSet());
    Map<String, JavaClassProfile> profilesByClassName = new LinkedHashMap<>();
    Map<String, List<String>> classesBySourcePath = new LinkedHashMap<>();
    for (CtType<?> type : model.getAllTypes()) {
      if (type.getPosition() == null || !type.getPosition().isValidPosition()) {
        continue;
      }
      JavaClassProfile profile = profileFactory.create(type, repoRoot, projectTypeNames);
      profilesByClassName.put(profile.qualifiedName(), profile);
      classesBySourcePath.compute(profile.sourcePath(), (ignored, existing) -> {
        java.util.List<String> classNames = existing == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(existing);
        classNames.add(profile.qualifiedName());
        classNames.sort(String::compareTo);
        return List.copyOf(classNames);
      });
    }
    return new JavaSourceSymbolCatalog(Map.copyOf(profilesByClassName), Map.copyOf(classesBySourcePath));
  }

  private CtModel buildModel(Path repoRoot, List<String> javaSourcePaths) {
    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setComplianceLevel(21);
    for (String sourcePath : javaSourcePaths) {
      launcher.addInputResource(repoRoot.resolve(sourcePath).toString());
    }
    launcher.buildModel();
    return launcher.getModel();
  }
}
