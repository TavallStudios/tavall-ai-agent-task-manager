package org.tavall.ai.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConfiguredCommandResolver {

  private ConfiguredCommandResolver() {
  }

  public static List<String> resolveCommand(String configuredCommand) {
    String trimmed = configuredCommand == null ? "" : configuredCommand.strip();
    if (trimmed.isBlank()) {
      throw new IllegalArgumentException("Configured command must not be blank.");
    }

    Path directFile = existingFile(trimmed);
    if (directFile != null) {
      return launcherFor(directFile);
    }

    List<String> tokens = tokenize(trimmed);
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException("Configured command must contain at least one token.");
    }

    Path firstTokenFile = existingFile(tokens.getFirst());
    if (firstTokenFile == null) {
      return tokens;
    }

    List<String> launcher = launcherFor(firstTokenFile);
    List<String> resolved = new ArrayList<>(launcher);
    resolved.addAll(tokens.subList(1, tokens.size()));
    return resolved;
  }

  private static Path existingFile(String token) {
    String candidate = stripMatchingQuotes(token);
    try {
      Path path = Path.of(candidate).toAbsolutePath().normalize();
      return Files.isRegularFile(path) ? path : null;
    } catch (InvalidPathException ignored) {
      return null;
    }
  }

  private static String stripMatchingQuotes(String value) {
    if (value.length() < 2) {
      return value;
    }
    char first = value.charAt(0);
    char last = value.charAt(value.length() - 1);
    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static List<String> launcherFor(Path file) {
    if (!isWindows()) {
      return List.of(file.toString());
    }
    String name = file.getFileName().toString().toLowerCase();
    if (name.endsWith(".cmd") || name.endsWith(".bat")) {
      return List.of("cmd.exe", "/c", file.toString());
    }
    if (name.endsWith(".ps1")) {
      return List.of("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", file.toString());
    }
    if (name.endsWith(".py") || readShebang(file).contains("python")) {
      return List.of("python", file.toString());
    }
    return List.of(file.toString());
  }

  private static String readShebang(Path file) {
    try {
      String firstLine = Files.readAllLines(file, StandardCharsets.UTF_8).stream().findFirst().orElse("");
      if (firstLine.startsWith("#!")) {
        return firstLine.substring(2).strip().toLowerCase();
      }
    } catch (IOException ignored) {
      return "";
    }
    return "";
  }

  private static List<String> tokenize(String command) {
    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    Character quote = null;

    for (int index = 0; index < command.length(); index++) {
      char character = command.charAt(index);
      if (quote != null) {
        if (character == quote) {
          quote = null;
        } else {
          current.append(character);
        }
        continue;
      }
      if (character == '"' || character == '\'') {
        quote = character;
        continue;
      }
      if (Character.isWhitespace(character)) {
        appendToken(tokens, current);
        continue;
      }
      current.append(character);
    }
    appendToken(tokens, current);
    return tokens;
  }

  private static void appendToken(List<String> tokens, StringBuilder current) {
    if (current.isEmpty()) {
      return;
    }
    tokens.add(current.toString());
    current.setLength(0);
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }
}

