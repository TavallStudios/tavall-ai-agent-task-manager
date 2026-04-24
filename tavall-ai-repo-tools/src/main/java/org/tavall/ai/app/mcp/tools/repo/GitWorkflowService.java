package org.tavall.ai.app.mcp.tools.repo;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GitWorkflowService {

  private final GitCommandRunner gitCommandRunner;
  private final GitWorkflowRenderer gitWorkflowRenderer;

  public GitWorkflowService(
      GitCommandRunner gitCommandRunner,
      GitWorkflowRenderer gitWorkflowRenderer
  ) {
    this.gitCommandRunner = gitCommandRunner;
    this.gitWorkflowRenderer = gitWorkflowRenderer;
  }

  public GitCommitPlanResponse plan(GitWorkflowRequest request) {
    Path repoPath = repoPath(request);
    GitWorkflowRenderer.GitWorkflowPlan plan = gitWorkflowRenderer.renderPlan(request, candidateFiles(repoPath, request));
    return new GitCommitPlanResponse(
        plan.branchName(),
        plan.subject(),
        plan.body(),
        plan.candidateFiles(),
        plan.groupingRecommendation(),
        plan.mixedConcernDetected()
    );
  }

  public PrepareGitBranchResponse prepareBranch(GitWorkflowRequest request) {
    Path repoPath = requireGitRepo(request);
    GitWorkflowRenderer.GitWorkflowPlan plan = gitWorkflowRenderer.renderPlan(request, candidateFiles(repoPath, request));
    String previousBranch = gitCommandRunner.currentBranch(repoPath);
    boolean created = !plan.branchName().equals(previousBranch) && !gitCommandRunner.branchExists(repoPath, plan.branchName());
    if (!plan.branchName().equals(previousBranch)) {
      gitCommandRunner.checkoutBranch(repoPath, plan.branchName(), created);
    }
    return new PrepareGitBranchResponse(
        plan.branchName(),
        previousBranch,
        gitCommandRunner.headRevision(repoPath),
        created
    );
  }

  public CreateGitCommitResponse createCommit(GitWorkflowRequest request) {
    Path repoPath = requireGitRepo(request);
    GitWorkflowRenderer.GitWorkflowPlan plan = gitWorkflowRenderer.renderPlan(request, candidateFiles(repoPath, request));
    if (plan.mixedConcernDetected() && !Boolean.TRUE.equals(request.allowMixedDomain())) {
      throw new IllegalStateException(plan.groupingRecommendation());
    }

    if (!plan.branchName().equals(gitCommandRunner.currentBranch(repoPath))) {
      boolean create = !gitCommandRunner.branchExists(repoPath, plan.branchName());
      gitCommandRunner.checkoutBranch(repoPath, plan.branchName(), create);
    }

    ensureNoUnexpectedStagedFiles(repoPath, request, plan.candidateFiles());
    stageChanges(repoPath, request, plan.candidateFiles());
    List<String> stagedFiles = gitCommandRunner.stagedFiles(repoPath);
    if (stagedFiles.isEmpty()) {
      throw new IllegalStateException("No staged files are available for commit.");
    }

    String commitHash = gitCommandRunner.createCommit(repoPath, plan.subject(), plan.body());
    return new CreateGitCommitResponse(
        plan.branchName(),
        commitHash,
        plan.subject(),
        plan.body(),
        gitCommandRunner.committedFiles(repoPath),
        plan.groupingRecommendation()
    );
  }

  private void ensureNoUnexpectedStagedFiles(
      Path repoPath,
      GitWorkflowRequest request,
      List<String> candidateFiles
  ) {
    if (request.filePaths() == null || request.filePaths().isEmpty()) {
      return;
    }
    List<String> unexpected = gitCommandRunner.stagedFiles(repoPath).stream()
        .filter(path -> candidateFiles.stream().noneMatch(candidate -> coversCandidate(candidate, path)))
        .toList();
    if (!unexpected.isEmpty()) {
      throw new IllegalStateException(
          "Unrelated files are already staged: "
              + String.join(", ", unexpected)
              + ". Commit or unstage them before using createGitCommit with filePaths."
      );
    }
  }

  private boolean coversCandidate(String candidatePath, String stagedPath) {
    String candidate = normalizePath(candidatePath);
    String staged = normalizePath(stagedPath);
    if (candidate.equals(staged)) {
      return true;
    }
    String prefix = candidate.endsWith("/") ? candidate.substring(0, candidate.length() - 1) : candidate;
    return !prefix.isBlank() && staged.startsWith(prefix + "/");
  }

  private String normalizePath(String path) {
    return path == null ? "" : path.strip().replace('\\', '/');
  }

  private void stageChanges(Path repoPath, GitWorkflowRequest request, List<String> candidateFiles) {
    if (request.filePaths() != null && !request.filePaths().isEmpty()) {
      List<String> stagedFiles = gitCommandRunner.stagedFiles(repoPath);
      List<String> pendingPaths = request.filePaths().stream()
          .filter(path -> !isAlreadyStaged(path, stagedFiles))
          .toList();
      if (!pendingPaths.isEmpty()) {
        gitCommandRunner.stageFiles(repoPath, pendingPaths);
      }
      return;
    }
    if (candidateFiles.isEmpty()) {
      throw new IllegalStateException("No changed files were detected for this concern.");
    }
    gitCommandRunner.stageAll(repoPath);
  }

  private boolean isAlreadyStaged(String candidatePath, List<String> stagedFiles) {
    String candidate = normalizePath(candidatePath);
    if (candidate.isBlank()) {
      return true;
    }
    String prefix = candidate.endsWith("/") ? candidate.substring(0, candidate.length() - 1) : candidate;
    return stagedFiles.stream()
        .map(this::normalizePath)
        .anyMatch(staged -> staged.equals(prefix) || staged.startsWith(prefix + "/"));
  }

  private List<String> candidateFiles(Path repoPath, GitWorkflowRequest request) {
    if (request.filePaths() != null && !request.filePaths().isEmpty()) {
      return request.filePaths().stream()
          .map(String::strip)
          .filter(path -> !path.isBlank())
          .toList();
    }
    if (gitCommandRunner.isGitRepository(repoPath)) {
      return gitCommandRunner.changedFiles(repoPath);
    }
    return List.of();
  }

  private Path requireGitRepo(GitWorkflowRequest request) {
    Path repoPath = repoPath(request);
    if (!gitCommandRunner.isGitRepository(repoPath)) {
      throw new IllegalStateException("repoPath must point at a git repository.");
    }
    return repoPath;
  }

  private Path repoPath(GitWorkflowRequest request) {
    if (request.repoPath() == null || request.repoPath().isBlank()) {
      throw new IllegalArgumentException("repoPath is required.");
    }
    return Path.of(request.repoPath()).toAbsolutePath().normalize();
  }
}

