package com.agenttaskmanager.app.mcp.tools.repo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class GitCommandRunner {

  private static final Duration TIMEOUT = Duration.ofSeconds(120);
  private static final int MAX_STAGE_COMMAND_LENGTH = 8_000;

  boolean isGitRepository(Path repoPath) {
    return succeeds(repoPath, "rev-parse", "--is-inside-work-tree");
  }

  List<String> changedFiles(Path repoPath) {
    return parseFiles(run(repoPath, "status", "--porcelain"));
  }

  String currentBranch(Path repoPath) {
    return runOrBlank(repoPath, "branch", "--show-current");
  }

  String headRevision(Path repoPath) {
    return run(repoPath, "rev-parse", "HEAD");
  }

  boolean branchExists(Path repoPath, String branchName) {
    return succeeds(repoPath, "show-ref", "--verify", "--quiet", "refs/heads/" + branchName);
  }

  void checkoutBranch(Path repoPath, String branchName, boolean create) {
    if (create) {
      run(repoPath, "checkout", "-b", branchName);
      return;
    }
    run(repoPath, "checkout", branchName);
  }

  void stageAll(Path repoPath) {
    run(repoPath, "add", "-A");
  }

  void stageFiles(Path repoPath, List<String> files) {
    List<String> batch = new ArrayList<>();
    int currentLength = "git add -A --".length();
    for (String file : files) {
      String normalized = file == null ? "" : file.strip();
      if (normalized.isBlank()) {
        continue;
      }
      int nextLength = currentLength + 1 + normalized.length();
      if (!batch.isEmpty() && nextLength > MAX_STAGE_COMMAND_LENGTH) {
        runStageBatch(repoPath, batch);
        batch = new ArrayList<>();
        currentLength = "git add -A --".length();
      }
      batch.add(normalized);
      currentLength += 1 + normalized.length();
    }
    if (!batch.isEmpty()) {
      runStageBatch(repoPath, batch);
    }
  }

  List<String> stagedFiles(Path repoPath) {
    return splitLines(runOrBlank(repoPath, "diff", "--cached", "--name-only"));
  }

  String createCommit(Path repoPath, String subject, String body) {
    run(repoPath, List.of("commit", "-m", subject, "-m", body));
    return headRevision(repoPath);
  }

  List<String> committedFiles(Path repoPath) {
    return splitLines(run(repoPath, "show", "--pretty=", "--name-only", "HEAD"));
  }

  String headBody(Path repoPath) {
    String payload = run(repoPath, "log", "-1", "--format=%s%x1f%b");
    int separator = payload.indexOf('\u001f');
    if (separator < 0 || separator + 1 >= payload.length()) {
      return "";
    }
    return payload.substring(separator + 1).strip();
  }

  String headSubject(Path repoPath) {
    String payload = run(repoPath, "log", "-1", "--format=%s");
    return payload.strip();
  }

  private List<String> parseFiles(String output) {
    List<String> files = new ArrayList<>();
    for (String line : splitLines(output)) {
      if (line.length() < 4) {
        continue;
      }
      String path = line.substring(3).trim();
      int renameIndex = path.indexOf(" -> ");
      if (renameIndex >= 0) {
        path = path.substring(renameIndex + 4).trim();
      }
      if (!path.isBlank()) {
        files.add(path);
      }
    }
    return files;
  }

  private List<String> splitLines(String output) {
    if (output == null || output.isBlank()) {
      return List.of();
    }
    return output.lines()
        .map(String::strip)
        .filter(line -> !line.isBlank())
        .toList();
  }

  private String runOrBlank(Path repoPath, String... command) {
    try {
      return run(repoPath, command);
    } catch (IllegalStateException exception) {
      return "";
    }
  }

  private boolean succeeds(Path repoPath, String... command) {
    try {
      run(repoPath, command);
      return true;
    } catch (IllegalStateException exception) {
      return false;
    }
  }

  private String run(Path repoPath, List<String> gitArgs) {
    List<String> command = new ArrayList<>();
    command.add("git");
    command.addAll(gitArgs);
    return runProcess(repoPath, command);
  }

  private String run(Path repoPath, String... gitArgs) {
    return run(repoPath, List.of(gitArgs));
  }

  private String runProcess(Path repoPath, List<String> command) {
    try {
      Process process = new ProcessBuilder(command)
          .directory(repoPath.toFile())
          .redirectErrorStream(true)
          .start();
      boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("Git command timed out: " + String.join(" ", command));
      }
      if (process.exitValue() != 0) {
        throw new IllegalStateException(output.isBlank() ? "Git command failed." : output);
      }
      return output;
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to run git command.", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Git command interrupted.", exception);
    }
  }

  private void runStageBatch(Path repoPath, List<String> files) {
    List<String> command = new ArrayList<>(List.of("add", "-A", "--"));
    command.addAll(files);
    run(repoPath, command);
  }
}
