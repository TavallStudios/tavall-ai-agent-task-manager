package com.agenttaskmanager.app.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class JavaLintMavenExecutor {

  private static final int ENGINE_TIMEOUT_SECONDS = 600;
  private static final int ENGINE_TIMEOUT_EXIT_CODE = 124;
  private static final int MAX_OUTPUT_CHARS = 50_000;

  EngineRunResult runGoal(Path repoRoot, List<String> arguments) {
    List<String> command = new ArrayList<>();
    command.add("mvn");
    command.addAll(arguments);

    Path outputPath = null;
    try {
      outputPath = Files.createTempFile("atm-java-lint-", ".log");
      Process process = new ProcessBuilder(command)
          .directory(repoRoot.toFile())
          .redirectErrorStream(true)
          .redirectOutput(outputPath.toFile())
          .start();

      boolean finished = process.waitFor(ENGINE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
      }
      int exitCode = finished ? process.exitValue() : ENGINE_TIMEOUT_EXIT_CODE;
      return new EngineRunResult(exitCode, readOutput(outputPath));
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      String output = exception.getMessage() == null ? "Lint engine failed." : exception.getMessage();
      return new EngineRunResult(-1, output);
    } finally {
      if (outputPath != null) {
        try {
          Files.deleteIfExists(outputPath);
        } catch (IOException ignored) {
          // Best effort cleanup for temporary lint output.
        }
      }
    }
  }

  private String readOutput(Path outputPath) throws IOException {
    String output = Files.readString(outputPath, StandardCharsets.UTF_8);
    if (output.length() <= MAX_OUTPUT_CHARS) {
      return output;
    }
    return output.substring(0, MAX_OUTPUT_CHARS) + "\n... lint output truncated ...";
  }

  record EngineRunResult(int exitCode, String output) {
  }
}
