package org.tavall.ai.app.support;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TestWorkspacePaths {

  private TestWorkspacePaths() {
  }

  public static Path repoRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("settings.gradle.kts"))
          && Files.isDirectory(current.resolve("tavall-ai-app"))
          && Files.isDirectory(current.resolve("tavall-ai-core"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Failed to locate the AgentTaskManager repository root.");
  }

  public static Path appModuleRoot() {
    return repoRoot().resolve("tavall-ai-app");
  }

  public static String fakeCodexCommand() {
    Path binRoot = appModuleRoot().resolve("src/test/resources/bin");
    if (isWindows()) {
      return binRoot.resolve("fake-codex.ps1").toString();
    }
    return binRoot.resolve("fake-codex").toString();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }
}

