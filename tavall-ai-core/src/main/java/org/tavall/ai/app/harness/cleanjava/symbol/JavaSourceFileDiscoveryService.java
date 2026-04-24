package org.tavall.ai.app.harness.cleanjava.symbol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class JavaSourceFileDiscoveryService {

  public List<String> listJavaSourcePaths(Path repoRoot) {
    Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
    try (Stream<Path> stream = Files.walk(normalizedRepoRoot)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(this::javaSource)
          .filter(path -> !excluded(path, normalizedRepoRoot))
          .map(path -> normalizedRepoRoot.relativize(path).toString().replace('\\', '/'))
          .sorted()
          .toList();
    } catch (IOException exception) {
      return List.of();
    }
  }

  public boolean hasJavaSources(Path repoRoot) {
    return !listJavaSourcePaths(repoRoot).isEmpty();
  }

  public List<String> filterJavaSourcePaths(List<String> candidatePaths) {
    if (candidatePaths == null) {
      return List.of();
    }
    return candidatePaths.stream()
        .map(this::normalize)
        .filter(path -> !path.isBlank())
        .filter(path -> path.toLowerCase(Locale.ROOT).endsWith(".java"))
        .distinct()
        .sorted()
        .toList();
  }

  private boolean javaSource(Path path) {
    return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java");
  }

  private boolean excluded(Path path, Path repoRoot) {
    Path relativePath = repoRoot.relativize(path);
    for (Path segment : relativePath) {
      String normalized = segment.toString().toLowerCase(Locale.ROOT);
      if ("target".equals(normalized)
          || ".git".equals(normalized)
          || ".idea".equals(normalized)
          || "node_modules".equals(normalized)) {
        return true;
      }
    }
    return false;
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.strip().replace('\\', '/');
  }
}

