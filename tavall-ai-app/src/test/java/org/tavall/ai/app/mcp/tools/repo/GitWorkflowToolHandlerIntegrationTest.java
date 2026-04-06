package org.tavall.ai.app.mcp.tools.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.mcp.McpToolHandlerIntegrationAssertions;
import org.tavall.ai.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

class GitWorkflowToolHandlerIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private GitWorkflowService gitWorkflowService;

  @Autowired
  private GitWorkflowToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterGitWorkflowTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(handlerTools, "planGitCommit", "prepareGitBranch", "createGitCommit");
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "planGitCommit", "prepareGitBranch", "createGitCommit");
  }

  @Test
  void shouldCreateVerboseCommitOnDeterministicBranch(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        """
        package example;

        public class FixtureApp {
          public String status() {
            return "updated";
          }
        }
        """,
        StandardCharsets.UTF_8
    );

    GitWorkflowRequest request = new GitWorkflowRequest(
        repoPath.toString(),
        "Changed",
        "ATM",
        "Fake Codex",
        "TJ",
        "1",
        "Fixture workflow update",
        "Recorded the fixture workflow update.",
        "git status",
        true,
        false,
        List.of("src/main/java/example/FixtureApp.java"),
        null,
        null,
        null,
        null
    );

    PrepareGitBranchResponse branchResponse = gitWorkflowService.prepareBranch(request);
    CreateGitCommitResponse commitResponse = gitWorkflowService.createCommit(request);

    assertEquals("atm-fake-codex-tj-v1", branchResponse.branchName());
    assertEquals("atm-fake-codex-tj-v1", run(repoPath, "git", "branch", "--show-current"));
    assertEquals("Changed: Fixture workflow update", commitResponse.subject());
    assertTrue(commitResponse.body().contains("What Changed:"));
    assertTrue(commitResponse.body().contains("Verification:"));
    assertFalse(commitResponse.commitHash().isBlank());
    assertTrue(commitResponse.committedFiles().contains("src/main/java/example/FixtureApp.java"));
  }

  @Test
  void shouldRejectRefactorBeforeFinalChange(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    Files.writeString(repoPath.resolve("README.md"), "# Fixture\nrefactor\n", StandardCharsets.UTF_8);

    GitWorkflowRequest request = new GitWorkflowRequest(
        repoPath.toString(),
        "Refactor",
        "ATM",
        "Harness",
        "TJ",
        "1",
        "Premature refactor",
        "Reworked the fixture before the concern was final.",
        "git status",
        false,
        false,
        List.of("README.md"),
        null,
        null,
        null,
        null
    );

    assertThrows(IllegalArgumentException.class, () -> gitWorkflowService.plan(request));
  }

  @Test
  void shouldRejectMixedConcernCommitWithoutOverride(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    Files.writeString(repoPath.resolve("README.md"), "# Fixture\nmixed\n", StandardCharsets.UTF_8);
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        """
        package example;

        public class FixtureApp {
          public String status() {
            return "mixed";
          }
        }
        """,
        StandardCharsets.UTF_8
    );

    GitWorkflowRequest request = new GitWorkflowRequest(
        repoPath.toString(),
        "Changed",
        "ATM",
        "Harness",
        "TJ",
        "1",
        "Mixed concern update",
        "Touched docs and source in one grouped change.",
        "git status",
        true,
        false,
        null,
        null,
        null,
        null,
        null
    );

    assertThrows(IllegalStateException.class, () -> gitWorkflowService.createCommit(request));
  }

  @Test
  void shouldRejectScopedCommitWhenUnrelatedFilesAreAlreadyStaged(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    Files.writeString(repoPath.resolve("README.md"), "# Fixture\nstaged\n", StandardCharsets.UTF_8);
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        """
        package example;

        public class FixtureApp {
          public String status() {
            return "scoped";
          }
        }
        """,
        StandardCharsets.UTF_8
    );
    run(repoPath, "git", "add", "README.md");

    GitWorkflowRequest request = new GitWorkflowRequest(
        repoPath.toString(),
        "Changed",
        "ATM",
        "Harness",
        "TJ",
        "1",
        "Scoped workflow update",
        "Attempted to commit only the fixture source file.",
        "git diff --cached --name-only",
        true,
        false,
        List.of("src/main/java/example/FixtureApp.java"),
        null,
        null,
        null,
        null
    );

    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> gitWorkflowService.createCommit(request));
    assertTrue(exception.getMessage().contains("README.md"));
    assertEquals("README.md", run(repoPath, "git", "diff", "--cached", "--name-only"));
    assertEquals("Initial fixture", run(repoPath, "git", "log", "-1", "--format=%s"));
  }

  @Test
  void shouldAllowScopedCommitWhenRequestedPathIsAnUntrackedDirectory(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    Files.createDirectories(repoPath.resolve("docs"));
    Files.writeString(repoPath.resolve("docs/notes.md"), "# Notes\n", StandardCharsets.UTF_8);

    GitWorkflowRequest request = new GitWorkflowRequest(
        repoPath.toString(),
        "Added",
        "ATM",
        "Docs",
        "TJ",
        "1",
        "Add fixture notes",
        "Added notes under a new docs directory.",
        "git show --name-only --pretty=",
        false,
        false,
        List.of("docs/"),
        null,
        null,
        null,
        null
    );

    CreateGitCommitResponse commitResponse = gitWorkflowService.createCommit(request);

    assertEquals("Added: Add fixture notes", commitResponse.subject());
    assertTrue(commitResponse.committedFiles().contains("docs/notes.md"));
  }

  @Test
  void shouldAllowScopedCommitWhenRequestedPathDeletesATrackedFile(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    Files.delete(repoPath.resolve("README.md"));

    GitWorkflowRequest request = new GitWorkflowRequest(
        repoPath.toString(),
        "Removed",
        "ATM",
        "Docs",
        "TJ",
        "1",
        "Remove fixture readme",
        "Removed the tracked fixture README.",
        "git show --name-status --pretty=",
        false,
        false,
        List.of("README.md"),
        null,
        null,
        null,
        null
    );

    CreateGitCommitResponse commitResponse = gitWorkflowService.createCommit(request);

    assertEquals("Removed: Remove fixture readme", commitResponse.subject());
    assertTrue(commitResponse.committedFiles().contains("README.md"));
  }

  @Test
  void shouldAllowScopedCommitWhenDeletionIsAlreadyStaged(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    Files.delete(repoPath.resolve("README.md"));
    run(repoPath, "git", "add", "-A", "--", "README.md");

    GitWorkflowRequest request = new GitWorkflowRequest(
        repoPath.toString(),
        "Removed",
        "ATM",
        "Docs",
        "TJ",
        "1",
        "Remove staged fixture readme",
        "Committed a README deletion that was already staged.",
        "git diff --cached --name-only",
        false,
        false,
        List.of("README.md"),
        null,
        null,
        null,
        null
    );

    CreateGitCommitResponse commitResponse = gitWorkflowService.createCommit(request);

    assertEquals("Removed: Remove staged fixture readme", commitResponse.subject());
    assertTrue(commitResponse.committedFiles().contains("README.md"));
  }

  private Path initializeFixtureRepo(Path repoPath) throws Exception {
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(
        repoPath.resolve("README.md"),
        "# Fixture\n",
        StandardCharsets.UTF_8
    );
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        """
        package example;

        public class FixtureApp {
          public String status() {
            return "ready";
          }
        }
        """,
        StandardCharsets.UTF_8
    );
    run(repoPath, "git", "init", "-b", "main");
    run(repoPath, "git", "config", "user.email", "integration@example.com");
    run(repoPath, "git", "config", "user.name", "Integration Test");
    run(repoPath, "git", "add", ".");
    run(repoPath, "git", "commit", "-m", "Initial fixture");
    return repoPath;
  }

  private String run(Path repoPath, String... command) throws Exception {
    Process process = new ProcessBuilder(command)
        .directory(repoPath.toFile())
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IOException(String.join(" ", command) + " failed: " + output);
    }
    return output;
  }
}

