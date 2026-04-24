package org.tavall.ai.app.orchestration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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

  public int commitCountSince(Path workspacePath, String baseRevision) {
    if (!isGitRepository(workspacePath)) {
      return 0;
    }
    String currentHead = currentRevision(workspacePath);
    if (currentHead.isBlank()) {
      return 0;
    }
    if (baseRevision == null || baseRevision.isBlank()) {
      return 1;
    }
    if (currentHead.equals(baseRevision.strip())) {
      return 0;
    }
    String count = runOrBlank(workspacePath, "git", "rev-list", "--count", baseRevision.strip() + "..HEAD").strip();
    if (count.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(count);
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  public boolean isGitRepository(Path workspacePath) {
    return Files.exists(workspacePath.resolve(".git")) || Files.isDirectory(workspacePath.resolve(".git"));
  }

  public List<WorkspaceFileChange> listWorkspaceChanges(Path workspacePath) {
    if (!isGitRepository(workspacePath)) {
      return List.of();
    }
    LinkedHashMap<String, WorkspaceFileChangeType> changes = new LinkedHashMap<>();
    mergeNameStatusChanges(changes, runOrBlank(workspacePath, "git", "diff", "--name-status", "--find-renames", "HEAD"));
    mergeUntrackedChanges(changes, runOrBlank(workspacePath, "git", "ls-files", "--others", "--exclude-standard"));
    return toWorkspaceChanges(changes);
  }

  public List<WorkspaceFileChange> listWorkspaceChangesSince(Path workspacePath, String baseRevision) {
    if (!isGitRepository(workspacePath)) {
      return List.of();
    }
    LinkedHashMap<String, WorkspaceFileChangeType> changes = new LinkedHashMap<>();
    if (baseRevision != null && !baseRevision.isBlank()) {
      mergeNameStatusChanges(
          changes,
          runOrBlank(workspacePath, "git", "diff", "--name-status", "--find-renames", baseRevision, "HEAD")
      );
    }
    mergeNameStatusChanges(changes, runOrBlank(workspacePath, "git", "diff", "--name-status", "--find-renames", "HEAD"));
    mergeUntrackedChanges(changes, runOrBlank(workspacePath, "git", "ls-files", "--others", "--exclude-standard"));
    return toWorkspaceChanges(changes);
  }

  private void mergeNameStatusChanges(LinkedHashMap<String, WorkspaceFileChangeType> changes, String diffOutput) {
    for (String line : diffOutput.lines().toList()) {
      if (line.isBlank()) {
        continue;
      }
      List<String> parts = List.of(line.split("\\t"));
      if (parts.isEmpty()) {
        continue;
      }
      String status = parts.getFirst();
      if (status.startsWith("R") && parts.size() >= 3) {
        changes.put(parts.get(1), WorkspaceFileChangeType.DELETE);
        changes.put(parts.get(2), WorkspaceFileChangeType.UPSERT);
        continue;
      }
      if (parts.size() < 2) {
        continue;
      }
      changes.put(parts.get(1), status.startsWith("D") ? WorkspaceFileChangeType.DELETE : WorkspaceFileChangeType.UPSERT);
    }
  }

  private void mergeUntrackedChanges(LinkedHashMap<String, WorkspaceFileChangeType> changes, String untrackedOutput) {
    for (String line : untrackedOutput.lines().toList()) {
      if (!line.isBlank()) {
        changes.put(line.strip(), WorkspaceFileChangeType.UPSERT);
      }
    }
  }

  private List<WorkspaceFileChange> toWorkspaceChanges(LinkedHashMap<String, WorkspaceFileChangeType> changes) {
    return changes.entrySet().stream()
        .map(entry -> new WorkspaceFileChange(entry.getKey(), entry.getValue()))
        .toList();
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

  public record WorkspaceFileChange(String relativePath, WorkspaceFileChangeType changeType) {
  }

  public enum WorkspaceFileChangeType {
    UPSERT,
    DELETE
  }
}

