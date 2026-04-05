package com.agenttaskmanager.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.config.ConfiguredCommandResolver;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import com.agenttaskmanager.app.support.TestWorkspacePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexRunExecutorServiceIntegrationTest extends IntegrationTestSupport {

  @org.springframework.beans.factory.annotation.Autowired
  private CodexRunExecutorService codexRunExecutorService;

  @org.springframework.beans.factory.annotation.Autowired
  private ContextualToolPolicyService contextualToolPolicyService;

  @org.springframework.beans.factory.annotation.Autowired
  private GitWorktreeManager gitWorktreeManager;

  @Test
  void shouldPassWhenRepoBackedWriteUsesDeterministicGitWorkflow(@TempDir Path tempDir) throws Exception {
    CodexRunResult result = runFixture(tempDir.resolve("fixture-pass"), "edit", "Apply workflow update", true);

    assertEquals(0, result.effectiveExitCode());
    assertTrue(result.toolPolicyAudit().passed());
    assertTrue(result.toolPolicyAudit().gitWorkflowRequired());
    assertTrue(result.toolPolicyAudit().commitCreated());
    assertEquals(1, result.toolPolicyAudit().commitCount());
    assertTrue(result.observedToolCalls().contains("plangitcommit"));
    assertTrue(result.observedToolCalls().contains("preparegitbranch"));
    assertTrue(result.observedToolCalls().contains("creategitcommit"));
  }

  @Test
  void shouldFailWhenRepoBackedWriteSkipsGitWorkflow(@TempDir Path tempDir) throws Exception {
    CodexRunResult result = runFixture(tempDir.resolve("fixture-missing"), "edit", "[skip-git-workflow] Apply update", true);

    assertEquals(97, result.effectiveExitCode());
    assertFalse(result.toolPolicyAudit().passed());
    assertTrue(result.toolPolicyAudit().gitWorkflowRequired());
    assertTrue(result.toolPolicyAudit().missingCalls().contains("plangitcommit"));
    assertTrue(result.toolPolicyAudit().missingCalls().contains("preparegitbranch"));
    assertTrue(result.toolPolicyAudit().missingCalls().contains("creategitcommit"));
  }

  @Test
  void shouldFailWhenGenericGitMutationToolIsObserved(@TempDir Path tempDir) throws Exception {
    CodexRunResult result = runFixture(tempDir.resolve("fixture-generic"), "edit", "[generic-git-mutation] Apply update", true);

    assertEquals(97, result.effectiveExitCode());
    assertFalse(result.toolPolicyAudit().passed());
    assertTrue(result.toolPolicyAudit().violations().stream().anyMatch(violation -> violation.contains("gitcommit")));
  }

  @Test
  void shouldFailWhenGitWorkflowSignalsToolsButCreatesNoCommit(@TempDir Path tempDir) throws Exception {
    CodexRunResult result = runFixture(tempDir.resolve("fixture-no-commit"), "edit", "[workflow-no-commit] Apply update", true);

    assertEquals(97, result.effectiveExitCode());
    assertFalse(result.toolPolicyAudit().passed());
    assertTrue(result.toolPolicyAudit().gitWorkflowRequired());
    assertFalse(result.toolPolicyAudit().commitCreated());
    assertEquals(0, result.toolPolicyAudit().commitCount());
    assertTrue(result.toolPolicyAudit().violations().stream().anyMatch(violation -> violation.contains("create a new commit")));
  }

  @Test
  void shouldFailWhenGitWorkflowCreatesMultipleCommitsForOnePrompt(@TempDir Path tempDir) throws Exception {
    CodexRunResult result = runFixture(tempDir.resolve("fixture-double-commit"), "edit", "[double-commit] Apply update", true);

    assertEquals(97, result.effectiveExitCode());
    assertFalse(result.toolPolicyAudit().passed());
    assertTrue(result.toolPolicyAudit().gitWorkflowRequired());
    assertTrue(result.toolPolicyAudit().commitCreated());
    assertEquals(2, result.toolPolicyAudit().commitCount());
    assertTrue(result.toolPolicyAudit().violations().stream().anyMatch(violation -> violation.contains("exactly one new commit")));
  }

  @Test
  void shouldAllowNoOpWriteRunWithoutCommit(@TempDir Path tempDir) throws Exception {
    CodexRunResult result = runFixture(tempDir.resolve("fixture-noop"), "edit", "[no-op] Inspect only", true);

    assertEquals(0, result.effectiveExitCode());
    assertTrue(result.toolPolicyAudit().passed());
    assertFalse(result.toolPolicyAudit().gitWorkflowRequired());
    assertFalse(result.toolPolicyAudit().commitCreated());
    assertEquals(0, result.toolPolicyAudit().commitCount());
    assertFalse(result.diffPresent());
  }

  @Test
  void shouldAllowReadOnlyRunWithoutCommit(@TempDir Path tempDir) throws Exception {
    CodexRunResult result = runFixture(tempDir.resolve("fixture-read-only"), "read-only", "[read-only] Summarize state", false);

    assertEquals(0, result.effectiveExitCode());
    assertTrue(result.toolPolicyAudit().passed());
    assertFalse(result.toolPolicyAudit().gitWorkflowRequired());
    assertFalse(result.toolPolicyAudit().commitCreated());
    assertEquals(0, result.toolPolicyAudit().commitCount());
    assertFalse(result.diffPresent());
  }

  @Test
  void shouldFailWhenNativeWindowsRunUsesShellCommand(@TempDir Path tempDir) throws Exception {
    CodexRunResult result = runFixture(
        tempDir.resolve("fixture-shell-native"),
        "read-only",
        "[read-only] [shell-command] Summarize state",
        false,
        CodexRuntimePlatform.WINDOWS_NATIVE
    );

    assertEquals(97, result.effectiveExitCode());
    assertFalse(result.toolPolicyAudit().passed());
    assertEquals("windows-native", result.toolPolicyAudit().runtimePlatform());
    assertTrue(result.toolPolicyAudit().forbiddenToolCalls().contains("shell_command"));
    assertTrue(result.toolPolicyAudit().violations().stream().anyMatch(violation -> violation.contains("shell_command")));
  }

  @Test
  void shouldAllowShellCommandWhenRunIsClassifiedAsWsl(@TempDir Path tempDir) throws Exception {
    CodexRunResult result = runFixture(
        tempDir.resolve("fixture-shell-wsl"),
        "read-only",
        "[read-only] [shell-command] Summarize state",
        false,
        CodexRuntimePlatform.WINDOWS_WSL
    );

    assertEquals(0, result.effectiveExitCode());
    assertTrue(result.toolPolicyAudit().passed());
    assertEquals("windows-wsl", result.toolPolicyAudit().runtimePlatform());
    assertTrue(result.toolPolicyAudit().forbiddenToolCalls().isEmpty());
  }

  @Test
  void shouldAllowShellCommandWhenRunIsNonWindows(@TempDir Path tempDir) throws Exception {
    CodexRunResult result = runFixture(
        tempDir.resolve("fixture-shell-non-windows"),
        "read-only",
        "[read-only] [shell-command] Summarize state",
        false,
        CodexRuntimePlatform.NON_WINDOWS
    );

    assertEquals(0, result.effectiveExitCode());
    assertTrue(result.toolPolicyAudit().passed());
    assertEquals("non-windows", result.toolPolicyAudit().runtimePlatform());
    assertTrue(result.toolPolicyAudit().forbiddenToolCalls().isEmpty());
  }

  private CodexRunResult runFixture(
      Path repoPath,
      String executionMode,
      String prompt,
      boolean repoBackedWriteRun
  ) throws Exception {
    return runFixture(repoPath, executionMode, prompt, repoBackedWriteRun, null);
  }

  private CodexRunResult runFixture(
      Path repoPath,
      String executionMode,
      String prompt,
      boolean repoBackedWriteRun,
      CodexRuntimePlatform runtimePlatformOverride
  ) throws Exception {
    initializeFixtureRepo(repoPath);
    Path outputFile = repoPath.resolve(".codex-run-output.txt");
    GitWorktreeManager.GitHeadState initialGitState = gitWorktreeManager.loadHeadState(repoPath);
    List<String> command = new ArrayList<>(ConfiguredCommandResolver.resolveCommand(TestWorkspacePaths.fakeCodexCommand()));
    command.add("-C");
    command.add(repoPath.toString());
    command.add("-s");
    command.add("read-only".equals(executionMode) ? "read-only" : "workspace-write");
    command.add("exec");
    command.add("--json");
    command.add("--output-last-message");
    command.add(outputFile.toString());
    command.add(prompt);
    return codexRunExecutorService.execute(new CodexRunRequest(
        command,
        repoPath,
        outputFile,
        initialGitState.headCommitHash(),
        "",
        contextualToolPolicyService.decide(executionMode, prompt, false, repoBackedWriteRun),
        ContextualToolPolicyService.HarnessMemoryEvidence.disabled(),
        event -> {
        },
        runtimePlatformOverride
    ));
  }

  private void initializeFixtureRepo(Path repoPath) throws Exception {
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(repoPath.resolve("README.md"), "# Codex Run Fixture\n", StandardCharsets.UTF_8);
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        """
        package example;

        public class FixtureApp {
        }
        """,
        StandardCharsets.UTF_8
    );
    run(repoPath, "git", "init", "-b", "main");
    run(repoPath, "git", "config", "user.email", "integration@example.com");
    run(repoPath, "git", "config", "user.name", "Integration Test");
    run(repoPath, "git", "add", ".");
    run(repoPath, "git", "commit", "-m", "Initial fixture");
  }

  private void run(Path repoPath, String... command) throws Exception {
    Process process = new ProcessBuilder(command)
        .directory(repoPath.toFile())
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IOException(String.join(" ", command) + " failed: " + output);
    }
  }
}
