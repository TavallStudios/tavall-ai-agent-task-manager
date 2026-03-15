package com.agenttaskmanager.app.orchestration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    if (!Files.exists(workspacePath.resolve(".git"))) {
      return "";
    }
    return run(workspacePath, "git", "diff", "--binary");
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
}
