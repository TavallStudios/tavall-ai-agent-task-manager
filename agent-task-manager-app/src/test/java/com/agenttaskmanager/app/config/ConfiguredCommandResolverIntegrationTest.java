package com.agenttaskmanager.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.support.TestWorkspacePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class ConfiguredCommandResolverIntegrationTest {

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void shouldLaunchConfiguredPs1CommandOnWindows(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("fake-codex-output.txt");
    List<String> command = new ArrayList<>(ConfiguredCommandResolver.resolveCommand(TestWorkspacePaths.fakeCodexCommand()));
    command.addAll(List.of(
        "-C",
        tempDir.toString(),
        "exec",
        "--json",
        "--output-last-message",
        outputFile.toString(),
        "[read-only] launcher regression"
    ));

    Process process = new ProcessBuilder(command)
        .directory(tempDir.toFile())
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();

    assertEquals(0, exitCode);
    assertTrue(output.contains("fake-thread"));
    assertTrue(Files.exists(outputFile));
    assertTrue(Files.readString(outputFile, StandardCharsets.UTF_8).contains("launcher regression"));
  }

  @Test
  void shouldResolvePs1CommandsThroughPowershellLauncher(@TempDir Path tempDir) throws IOException {
    String originalOsName = System.getProperty("os.name");
    Path scriptPath = tempDir.resolve("fixture.ps1");
    Files.writeString(scriptPath, "Write-Output 'fixture'", StandardCharsets.UTF_8);
    try {
      System.setProperty("os.name", "Windows 11");
      List<String> command = ConfiguredCommandResolver.resolveCommand(scriptPath.toString());

      assertEquals("powershell.exe", command.getFirst());
      assertEquals("-ExecutionPolicy", command.get(1));
      assertEquals("Bypass", command.get(2));
      assertEquals("-File", command.get(3));
      assertEquals(scriptPath.toAbsolutePath().normalize().toString(), command.get(4));
    } finally {
      if (originalOsName == null) {
        System.clearProperty("os.name");
      } else {
        System.setProperty("os.name", originalOsName);
      }
    }
  }
}
