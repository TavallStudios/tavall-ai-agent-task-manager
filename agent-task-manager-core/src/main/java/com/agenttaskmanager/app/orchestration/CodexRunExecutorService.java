package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.bridge.CodexEventMessage;
import com.agenttaskmanager.app.bridge.CodexJsonEventParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class CodexRunExecutorService {

  private final CodexJsonEventParser codexJsonEventParser;
  private final CodexRuntimePlatformDetector codexRuntimePlatformDetector;
  private final ContextualToolPolicyService contextualToolPolicyService;
  private final GitWorktreeManager gitWorktreeManager;

  public CodexRunExecutorService(
      CodexJsonEventParser codexJsonEventParser,
      CodexRuntimePlatformDetector codexRuntimePlatformDetector,
      ContextualToolPolicyService contextualToolPolicyService,
      GitWorktreeManager gitWorktreeManager
  ) {
    this.codexJsonEventParser = codexJsonEventParser;
    this.codexRuntimePlatformDetector = codexRuntimePlatformDetector;
    this.contextualToolPolicyService = contextualToolPolicyService;
    this.gitWorktreeManager = gitWorktreeManager;
  }

  public CodexRunResult execute(CodexRunRequest request) {
    Process process = start(request);
    CodexRuntimePlatform runtimePlatform = request.runtimePlatformOverride() == null
        ? codexRuntimePlatformDetector.detectCurrentPlatform()
        : request.runtimePlatformOverride();
    Set<String> observedToolCalls = new LinkedHashSet<>();
    Set<CodexToolCallObservation> observedToolCallDetails = new LinkedHashSet<>();
    String stdout = readStream(process, true, observedToolCalls, observedToolCallDetails, request.eventConsumer());
    String stderr = readStream(process, false, observedToolCalls, request.eventConsumer());
    int exitCode = waitFor(process);
    String finalMessage = readOutputFile(request);
    GitWorktreeManager.GitHeadState finalGitState = gitWorktreeManager.loadHeadState(request.workspacePath());
    String diffText = gitWorktreeManager.captureDiffSince(request.workspacePath(), request.baseRevision());
    int commitCountSinceBase = gitWorktreeManager.commitCountSince(request.workspacePath(), request.baseRevision());
    ContextualToolPolicyService.ToolPolicyAudit toolPolicyAudit = contextualToolPolicyService.audit(
        request.toolPolicyDecision(),
        observedToolCallDetails,
        finalMessage,
        diffText,
        new ContextualToolPolicyService.GitWorkflowEvidence(
            finalGitState.gitRepository(),
            finalGitState.branchName(),
            request.baseRevision(),
            finalGitState.headCommitHash(),
            commitCountSinceBase,
            finalGitState.headSubject(),
            finalGitState.headBody()
        ),
        request.harnessMemoryEvidence(),
        runtimePlatform
    );
    int effectiveExitCode = toolPolicyAudit.passed() ? exitCode : 97;
    return new CodexRunResult(
        stdout,
        stderr,
        exitCode,
        effectiveExitCode,
        finalMessage,
        Set.copyOf(observedToolCalls),
        diffText,
        toolPolicyAudit.diffPresent(),
        finalGitState,
        toolPolicyAudit
    );
  }

  private Process start(CodexRunRequest request) {
    try {
      return new ProcessBuilder(request.command())
          .directory(request.workspacePath().toFile())
          .redirectErrorStream(false)
          .start();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to start Codex execution.", exception);
    }
  }

  private String readStream(
      Process process,
      boolean stdout,
      Set<String> observedToolCalls,
      Set<CodexToolCallObservation> observedToolCallDetails,
      Consumer<CodexEventMessage> eventConsumer
  ) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        stdout ? process.getInputStream() : process.getErrorStream(),
        StandardCharsets.UTF_8
    ))) {
      StringBuilder output = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        if (stdout) {
          for (CodexEventMessage message : codexJsonEventParser.parseLine(line)) {
            if ("tool-call".equals(message.kind())) {
              String signature = contextualToolPolicyService.normalizeObservedSignature(message.body());
              if (!signature.isBlank()) {
                observedToolCalls.add(signature);
                observedToolCallDetails.add(new CodexToolCallObservation(signature, message.toolName()));
              }
            }
            if (eventConsumer != null) {
              eventConsumer.accept(message);
            }
          }
        }
        output.append(line).append('\n');
      }
      return output.toString().strip();
    } catch (IOException exception) {
      return exception.getMessage() == null ? exception.toString() : exception.getMessage();
    }
  }

  private String readStream(
      Process process,
      boolean stdout,
      Set<String> observedToolCalls,
      Consumer<CodexEventMessage> eventConsumer
  ) {
    return readStream(process, stdout, observedToolCalls, new LinkedHashSet<>(), eventConsumer);
  }

  private int waitFor(Process process) {
    try {
      return process.waitFor();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return -1;
    }
  }

  private String readOutputFile(CodexRunRequest request) {
    try {
      if (Files.exists(request.outputFile())) {
        String contents = Files.readString(request.outputFile(), StandardCharsets.UTF_8).strip();
        if (!contents.isBlank()) {
          return contents;
        }
      }
    } catch (IOException ignored) {
      // Fall back to the provided summary when the output file is unreadable.
    }
    return request.finalResponseFallback() == null ? "" : request.finalResponseFallback().strip();
  }
}
