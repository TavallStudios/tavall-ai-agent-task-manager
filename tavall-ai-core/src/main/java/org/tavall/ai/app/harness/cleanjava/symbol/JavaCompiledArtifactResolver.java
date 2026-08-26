package org.tavall.ai.app.harness.cleanjava.symbol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class JavaCompiledArtifactResolver {

  private static final Duration COMPILE_TIMEOUT = Duration.ofSeconds(60);

  public List<Path> existingClasspathRoots(Path repoRoot) {
    Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
    try (Stream<Path> stream = Files.walk(normalizedRepoRoot, 6)) {
      return stream
          .filter(Files::isDirectory)
          .filter(path -> {
            String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            return normalized.endsWith("/build/classes/java/main")
                || normalized.endsWith("/build/classes/java/test");
          })
          .sorted()
          .toList();
    } catch (IOException exception) {
      return List.of();
    }
  }

  public JavaCompiledArtifacts compileChangedSources(Path repoRoot, List<String> changedSourcePaths) {
    List<Path> moduleRoots = moduleRoots(repoRoot, changedSourcePaths);
    if (moduleRoots.isEmpty()) {
      return new JavaCompiledArtifacts("skipped-no-gradle-build", List.of(), existingClasspathRoots(repoRoot), "");
    }
    StringBuilder output = new StringBuilder();
    for (Path moduleRoot : moduleRoots) {
      Command command = command(moduleRoot);
      CommandResult result = run(command, moduleRoot);
      output.append(result.output()).append('\n');
      if (!result.passed()) {
        return new JavaCompiledArtifacts(
            "compile-failed",
            moduleRoots,
            existingClasspathRoots(repoRoot),
            output.toString().strip()
        );
      }
    }
    return new JavaCompiledArtifacts("compiled", moduleRoots, existingClasspathRoots(repoRoot), output.toString().strip());
  }

  private List<Path> moduleRoots(Path repoRoot, List<String> changedSourcePaths) {
    Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
    Set<Path> moduleRoots = new LinkedHashSet<>();
    for (String changedSourcePath : changedSourcePaths == null ? List.<String>of() : changedSourcePaths) {
      Path sourcePath = normalizedRepoRoot.resolve(changedSourcePath).normalize();
      Path moduleRoot = nearestGradleBuildDirectory(sourcePath, normalizedRepoRoot);
      if (moduleRoot != null) {
        moduleRoots.add(moduleRoot);
      }
    }
    if (!moduleRoots.isEmpty()) {
      return List.copyOf(moduleRoots);
    }
    if (hasGradleBuild(normalizedRepoRoot)) {
      return List.of(normalizedRepoRoot);
    }
    return List.of();
  }

  private Path nearestGradleBuildDirectory(Path startPath, Path repoRoot) {
    Path current = Files.isDirectory(startPath) ? startPath : startPath.getParent();
    while (current != null && current.startsWith(repoRoot)) {
      if (hasGradleBuild(current)) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  private Command command(Path moduleRoot) {
    if (isWindows() && Files.isRegularFile(moduleRoot.resolve("gradlew.bat"))) {
      return new Command(List.of(
          moduleRoot.resolve("gradlew.bat").toString(),
          "--no-daemon",
          "--max-workers=1",
          "classes",
          "testClasses"
      ));
    }
    if (!isWindows() && Files.isRegularFile(moduleRoot.resolve("gradlew"))) {
      return new Command(List.of(
          moduleRoot.resolve("gradlew").toString(),
          "--no-daemon",
          "--max-workers=1",
          "classes",
          "testClasses"
      ));
    }
    return new Command(List.of("gradle", "--no-daemon", "--max-workers=1", "classes", "testClasses"));
  }

  private boolean hasGradleBuild(Path path) {
    return Files.isRegularFile(path.resolve("settings.gradle.kts"))
        || Files.isRegularFile(path.resolve("settings.gradle"))
        || Files.isRegularFile(path.resolve("build.gradle.kts"))
        || Files.isRegularFile(path.resolve("build.gradle"));
  }

  private CommandResult run(Command command, Path moduleRoot) {
    try {
      Process process = new ProcessBuilder(command.arguments())
          .directory(moduleRoot.toFile())
          .redirectErrorStream(true)
          .start();
      boolean finished = process.waitFor(COMPILE_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return new CommandResult(finished && process.exitValue() == 0, snippet(output));
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return new CommandResult(false, snippet(exception.getMessage()));
    }
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private String snippet(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String normalized = value.replaceAll("\\s+", " ").strip();
    return normalized.length() <= 600 ? normalized : normalized.substring(0, 600) + "...";
  }

  private record Command(List<String> arguments) {
  }

  private record CommandResult(boolean passed, String output) {
  }
}
