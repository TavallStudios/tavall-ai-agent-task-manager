package com.agenttaskmanager.app.orchestration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GitWorktreeManager {

  public Path prepareWorkspace(Path repoPath, String taskId, String workerTaskId) {
    Path worktreePath = Path.of("target", "atm", "worktrees", taskId, workerTaskId).toAbsolutePath();
    try {
      Files.createDirectories(worktreePath.getParent());
      if (Files.exists(worktreePath.resolve(".git")) || Files.exists(worktreePath.resolve(".git").getParent())) {
        return worktreePath;
      }
      if (!Files.exists(repoPath.resolve(".git"))) {
        Files.createDirectories(worktreePath);
        return worktreePath;
      }
      run(repoPath, "git", "worktree", "add", "--force", "--detach", worktreePath.toString(), "HEAD");
      return worktreePath;
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to prepare worker worktree.", exception);
    }
  }

  public String captureDiff(Path workspacePath) {
    return captureDiffSince(workspacePath, currentRevision(workspacePath));
  }

  public String captureDiffSince(Path workspacePath, String baseRevision) {
    if (!isGitRepository(workspacePath)) {
      return "";
    }
    if (baseRevision == null || baseRevision.isBlank()) {
      return run(workspacePath, "git", "diff", "--binary");
    }
    return run(workspacePath, "git", "diff", "--binary", baseRevision);
  }

  public String currentRevision(Path workspacePath) {
    if (!isGitRepository(workspacePath)) {
      return "";
    }
    return run(workspacePath, "git", "rev-parse", "HEAD").strip();
  }

  public GitHeadState loadHeadState(Path workspacePath) {
    if (!isGitRepository(workspacePath)) {
      return new GitHeadState(false, "", "", "", "");
    }
    String branchName = runOrBlank(workspacePath, "git", "branch", "--show-current").strip();
    String payload = run(workspacePath, "git", "log", "-1", "--format=%H%x1f%s%x1f%b");
    List<String> parts = List.of(payload.split("\u001f", -1));
    String headCommitHash = parts.isEmpty() ? "" : parts.getFirst().strip();
    String headSubject = parts.size() < 2 ? "" : parts.get(1).strip();
    String headBody = parts.size() < 3 ? "" : parts.get(2).strip();
    return new GitHeadState(true, branchName, headCommitHash, headSubject, headBody);
  }

  public boolean isGitRepository(Path workspacePath) {
    return Files.exists(workspacePath.resolve(".git")) || Files.isDirectory(workspacePath.resolve(".git"));
  }

  private String run(Path directory, String... command) {
    try {
      Process process = new ProcessBuilder(command)
          .directory(directory.toFile())
          .redirectErrorStream(true)
          .start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = process.waitFor();
      if (exitCode == 0) {
        return output;
      }
      throw new IllegalStateException(String.join(" ", command) + " failed: " + output);
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Failed to run git command.", exception);
    }
  }

  private String runOrBlank(Path directory, String... command) {
    try {
      return run(directory, command);
    } catch (IllegalStateException exception) {
      return "";
    }
  }

  public record GitHeadState(
      boolean gitRepository,
      String branchName,
      String headCommitHash,
      String headSubject,
      String headBody
  ) {
  }
}
