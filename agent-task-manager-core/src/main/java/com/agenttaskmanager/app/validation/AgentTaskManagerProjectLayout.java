package com.agenttaskmanager.app.validation;

import cache.CacheDomain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AgentTaskManagerProjectLayout {

  private static final String APPLICATION_PACKAGE = "com.agenttaskmanager.app";
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
    if (!hasProjectMarkers(normalized) || !hasAgentTaskManagerPom(normalized)) {
      return false;
    }
    return hasSingleModuleLayout(normalized) || hasMultiModuleLayout(normalized);
  }

  private static boolean hasProjectMarkers(Path repoRoot) {
    return Files.isRegularFile(repoRoot.resolve("AGENTS.md"))
        && Files.isRegularFile(repoRoot.resolve("RULES.md"))
        && Files.isRegularFile(repoRoot.resolve("pom.xml"));
  }

  private static boolean hasAgentTaskManagerPom(Path repoRoot) {
    try {
      String pomBody = Files.readString(repoRoot.resolve("pom.xml"), StandardCharsets.UTF_8);
      return pomBody.contains("agent-task-manager");
    } catch (IOException exception) {
      return false;
    }
  }

  private static boolean hasSingleModuleLayout(Path repoRoot) {
    return Files.isDirectory(repoRoot.resolve("src/main/java"))
        && Files.isDirectory(repoRoot.resolve("src/test/java"));
  }

  private static boolean hasMultiModuleLayout(Path repoRoot) {
    return Files.isDirectory(repoRoot.resolve("agent-task-manager-core/src/main/java"))
        && Files.isDirectory(repoRoot.resolve("agent-task-manager-app/src/main/java"))
        && Files.isDirectory(repoRoot.resolve("spring-webview/src/main/java"));
  }
}
