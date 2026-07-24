package org.tavall.ai.app.validation;

import cache.CacheDomain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AgentTaskManagerProjectLayout {

  private static final String APPLICATION_PACKAGE = "org.tavall.ai.app";
  private static final List<String> CYCLE_BOUNDARIES = List.of(
      "bridge",
      "cli",
      "dashboard",
      "mcp",
      "orchestration",
      "persistence",
      "service",
      "validation",
      "web"
  );

  private AgentTaskManagerProjectLayout() {
  }

  public static String applicationPackage() {
    return APPLICATION_PACKAGE;
  }

  public static String cachePackage() {
    return CacheDomain.class.getPackageName();
  }

  public static List<String> cyclePackages() {
    return CYCLE_BOUNDARIES.stream()
        .map(boundary -> applicationPackage() + "." + boundary)
        .toList();
  }

  public static String slicePattern() {
    return applicationPackage() + ".(*)..";
  }

  public static boolean isProjectRoot(Path repoRoot) {
    Path normalized = repoRoot.toAbsolutePath().normalize();
    if (!hasProjectMarkers(normalized) || !hasAgentTaskManagerBuild(normalized)) {
      return false;
    }
    return hasSingleModuleLayout(normalized) || hasMultiModuleLayout(normalized);
  }

  public static boolean isGradleProjectRoot(Path repoRoot) {
    Path normalized = repoRoot.toAbsolutePath().normalize();
    boolean hasWrapper = Files.isRegularFile(normalized.resolve("gradlew"))
        || Files.isRegularFile(normalized.resolve("gradlew.bat"));
    return hasWrapper
        && Files.isRegularFile(normalized.resolve("settings.gradle.kts"))
        && Files.isRegularFile(normalized.resolve("build.gradle.kts"))
        && (hasSingleModuleLayout(normalized) || hasMultiModuleLayout(normalized));
  }

  private static boolean hasProjectMarkers(Path repoRoot) {
    return Files.isRegularFile(repoRoot.resolve("settings.gradle.kts"))
        && Files.isRegularFile(repoRoot.resolve("build.gradle.kts"))
        && Files.isRegularFile(repoRoot.resolve("docs/RULES.md"));
  }

  private static boolean hasAgentTaskManagerBuild(Path repoRoot) {
    try {
      String buildBody = Files.readString(repoRoot.resolve("settings.gradle.kts"));
      return buildBody.contains("tavall-ai");
    } catch (IOException exception) {
      return false;
    }
  }

  private static boolean hasSingleModuleLayout(Path repoRoot) {
    return Files.isDirectory(repoRoot.resolve("src/main/java"))
        && Files.isDirectory(repoRoot.resolve("src/test/java"));
  }

  private static boolean hasMultiModuleLayout(Path repoRoot) {
    return Files.isDirectory(repoRoot.resolve("tavall-ai-core/src/main/java"))
        && Files.isDirectory(repoRoot.resolve("tavall-ai-app/src/main/java"))
        && Files.isDirectory(repoRoot.resolve("tavall-ai-spring-webview/src/main/java"));
  }
}
