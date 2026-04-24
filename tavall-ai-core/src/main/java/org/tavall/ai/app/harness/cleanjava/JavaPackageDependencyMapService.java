package org.tavall.ai.app.harness.cleanjava;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class JavaPackageDependencyMapService {

  private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
  private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([\\w.*]+)\\s*;");

  public Map<String, Object> summarize(Path repoRoot) {
    Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
    Map<String, Set<String>> importsByPackage = new TreeMap<>();
    List<Map<String, Object>> files = new ArrayList<>();

    try (Stream<Path> stream = Files.walk(normalizedRepoRoot)) {
      List<Path> javaFiles = stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
          .sorted()
          .toList();
      for (Path file : javaFiles) {
        addFileSummary(normalizedRepoRoot, file, importsByPackage, files);
      }
    } catch (IOException exception) {
      return Map.of(
          "javaFileCount", 0,
          "packages", List.of(),
          "packageDependencies", Map.of(),
          "files", List.of(),
          "error", exception.getMessage()
      );
    }

    Map<String, List<String>> packageDependencies = new LinkedHashMap<>();
    importsByPackage.forEach((packageName, imports) -> packageDependencies.put(packageName, List.copyOf(imports)));
    return Map.of(
        "javaFileCount", files.size(),
        "packages", List.copyOf(packageDependencies.keySet()),
        "packageDependencies", packageDependencies,
        "files", List.copyOf(files)
    );
  }

  private void addFileSummary(
      Path repoRoot,
      Path file,
      Map<String, Set<String>> importsByPackage,
      List<Map<String, Object>> files
  ) throws IOException {
    String packageName = "";
    Set<String> imports = new TreeSet<>();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      var packageMatcher = PACKAGE_PATTERN.matcher(line);
      if (packageMatcher.find()) {
        packageName = packageMatcher.group(1);
        continue;
      }
      var importMatcher = IMPORT_PATTERN.matcher(line);
      if (importMatcher.find()) {
        imports.add(importMatcher.group(1));
      }
    }
    importsByPackage.computeIfAbsent(packageName, ignored -> new TreeSet<>()).addAll(imports);
    files.add(Map.of(
        "path", repoRoot.relativize(file).toString().replace('\\', '/'),
        "packageName", packageName,
        "imports", List.copyOf(imports)
    ));
  }
}

