package com.agenttaskmanager.app.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class DirectRepoToolExecutionService {

  private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(20);

  public boolean supports(DownstreamMcpToolCall call) {
    return switch (call.serverName() + ":" + call.toolName()) {
      case "filesystem:list_directory", "git:git_status", "git:git_diff_unstaged", "ripgrep:list-files",
          "ripgrep:advanced-search" -> true;
      default -> false;
    };
  }

  public DownstreamMcpToolResult executeFallback(
      DownstreamMcpToolCall call,
      String stderr,
      Exception originalException,
      long startedAt
  ) {
    try {
      return switch (call.serverName() + ":" + call.toolName()) {
        case "filesystem:list_directory" -> completed(call, directoryListing(call.arguments()), stderr, startedAt);
        case "git:git_status" -> gitOutput(call, List.of("git", "status", "--short"), stderr, startedAt);
        case "git:git_diff_unstaged" -> gitOutput(
            call,
            List.of("git", "diff", "--unified=" + readInt(call.arguments(), "context_lines", 20)),
            stderr,
            startedAt
        );
        case "ripgrep:list-files" -> completed(call, fileListing(call.arguments()), stderr, startedAt);
        case "ripgrep:advanced-search" -> completed(call, searchResults(call.arguments()), stderr, startedAt);
        default -> error(call, stderr, originalException.getMessage(), startedAt);
      };
    } catch (Exception fallbackException) {
      String message = originalException.getMessage() + " | Fallback failed: " + fallbackException.getMessage();
      return error(call, stderr, message, startedAt);
    }
  }

  private DownstreamMcpToolResult gitOutput(
      DownstreamMcpToolCall call,
      List<String> command,
      String stderr,
      long startedAt
  ) throws IOException, InterruptedException {
    CommandResult result = runCommand(command, repoPath(call.arguments()));
    if (result.exitCode() != 0) {
      return error(call, stderr, result.output(), startedAt);
    }
    Map<String, Object> structuredContent = Map.of("output", result.output());
    return completed(call, structuredContent, stderr, startedAt);
  }

  private Map<String, Object> directoryListing(Map<String, Object> arguments) throws IOException {
    Path path = repoPath(arguments);
    List<Map<String, Object>> entries = new ArrayList<>();
    try (Stream<Path> children = Files.list(path)) {
      children.sorted(Comparator.comparing(child -> child.getFileName().toString()))
          .forEach(child -> entries.add(Map.of(
              "name", child.getFileName().toString(),
              "path", child.toAbsolutePath().normalize().toString(),
              "type", Files.isDirectory(child) ? "directory" : "file"
          )));
    }
    return Map.of("path", path.toString(), "entries", entries);
  }

  private Map<String, Object> fileListing(Map<String, Object> arguments) throws IOException {
    Path path = repoPath(arguments);
    String fileType = readString(arguments, "fileType");
    List<String> files;
    try (Stream<Path> stream = Files.walk(path)) {
      files = stream
          .filter(Files::isRegularFile)
          .filter(file -> !isGitMetadata(path, file))
          .filter(file -> matchesFileType(file, fileType))
          .map(file -> file.toAbsolutePath().normalize().toString())
          .sorted()
          .toList();
    }
    return Map.of("path", path.toString(), "files", files);
  }

  private Map<String, Object> searchResults(Map<String, Object> arguments) throws IOException {
    Path path = repoPath(arguments);
    String pattern = readString(arguments, "pattern");
    int maxResults = readInt(arguments, "maxResults", 20);
    List<Map<String, Object>> matches = new ArrayList<>();
    try (Stream<Path> files = Files.walk(path)) {
      for (Path file : files.filter(Files::isRegularFile).filter(item -> !isGitMetadata(path, item)).sorted().toList()) {
        try {
          List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
          for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.contains(pattern)) {
              matches.add(Map.of(
                  "path", file.toAbsolutePath().normalize().toString(),
                  "lineNumber", index + 1,
                  "line", line
              ));
              if (matches.size() >= maxResults) {
                return Map.of("matches", matches);
              }
            }
          }
        } catch (IOException | UncheckedIOException ignored) {
          // Skip unreadable or binary files so repository metadata does not break local search fallback.
        }
      }
    }
    return Map.of("matches", matches);
  }

  private CommandResult runCommand(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command)
        .directory(workingDirectory.toFile())
        .redirectErrorStream(true)
        .start();
    boolean finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new IllegalStateException("Command timed out: " + String.join(" ", command));
    }
    return new CommandResult(
        process.exitValue(),
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip()
    );
  }

  private Path repoPath(Map<String, Object> arguments) {
    String path = readString(arguments, "repo_path");
    if (path.isBlank()) {
      path = readString(arguments, "path");
    }
    return Path.of(path).toAbsolutePath().normalize();
  }

  private boolean matchesFileType(Path path, String fileType) {
    if (fileType == null || fileType.isBlank()) {
      return true;
    }
    return path.getFileName().toString().toLowerCase().endsWith("." + fileType.toLowerCase());
  }

  private boolean isGitMetadata(Path root, Path path) {
    Path relative = root.relativize(path);
    return relative.startsWith(".git");
  }

  private String readString(Map<String, Object> arguments, String key) {
    Object value = arguments.get(key);
    return value == null ? "" : String.valueOf(value);
  }

  private int readInt(Map<String, Object> arguments, String key, int defaultValue) {
    Object value = arguments.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Integer.parseInt(text);
    }
    return defaultValue;
  }

  private DownstreamMcpToolResult completed(
      DownstreamMcpToolCall call,
      Map<String, Object> structuredContent,
      String stderr,
      long startedAt
  ) {
    return new DownstreamMcpToolResult(
        call.key(),
        call.serverName(),
        call.toolName(),
        "completed",
        structuredContent,
        structuredContent.toString(),
        stderr,
        null,
        durationMs(startedAt)
    );
  }

  private DownstreamMcpToolResult error(DownstreamMcpToolCall call, String stderr, String message, long startedAt) {
    return new DownstreamMcpToolResult(
        call.key(),
        call.serverName(),
        call.toolName(),
        "error",
        null,
        null,
        stderr,
        message,
        durationMs(startedAt)
    );
  }

  private long durationMs(long startedAt) {
    return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
  }

  private record CommandResult(int exitCode, String output) {
  }
}
