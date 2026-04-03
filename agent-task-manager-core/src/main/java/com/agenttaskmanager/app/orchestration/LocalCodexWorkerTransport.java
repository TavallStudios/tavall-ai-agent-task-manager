package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.bridge.CodexDeterministicConfigService;
import com.agenttaskmanager.app.bridge.CodexEventMessage;
import com.agenttaskmanager.app.bridge.CodexJsonEventParser;
import com.agenttaskmanager.app.cleanjava.CleanJavaHarnessValidator;
import com.agenttaskmanager.app.config.ConfiguredCommandResolver;
import com.agenttaskmanager.app.config.OrchestrationProperties;
import com.agenttaskmanager.app.harness.approval.HarnessApprovalGateResult;
import com.agenttaskmanager.app.model.KnownRepo;
import com.agenttaskmanager.app.model.orchestration.ArtifactRecord;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerExecutionRequest;
import com.agenttaskmanager.app.model.orchestration.WorkerExecutionResult;
import com.agenttaskmanager.app.model.orchestration.WorkerRunSummary;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
import com.agenttaskmanager.app.persistence.postgres.WorkerTaskRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LocalCodexWorkerTransport implements WorkerTransport {

  private final ArtifactService artifactService;
  private final CleanJavaHarnessValidator cleanJavaHarnessValidator;
  private final GitWorktreeManager gitWorktreeManager;
  private final OrchestrationProperties orchestrationProperties;
  private final TaskPoolService taskPoolService;
  private final WorkerLifecycleService workerLifecycleService;
  private final PromptMemoryCaptureService promptMemoryCaptureService;
  private final com.agenttaskmanager.app.service.RepoCatalogService repoCatalogService;
  private final WorkerPromptFactory workerPromptFactory;
  private final WorkerTaskRepository workerTaskRepository;
  private final CodexJsonEventParser codexJsonEventParser;
  private final CodexDeterministicConfigService codexDeterministicConfigService;
  private final ContextualToolPolicyService contextualToolPolicyService;

  public LocalCodexWorkerTransport(
      ArtifactService artifactService,
      CleanJavaHarnessValidator cleanJavaHarnessValidator,
      GitWorktreeManager gitWorktreeManager,
      OrchestrationProperties orchestrationProperties,
      TaskPoolService taskPoolService,
      WorkerLifecycleService workerLifecycleService,
      PromptMemoryCaptureService promptMemoryCaptureService,
      com.agenttaskmanager.app.service.RepoCatalogService repoCatalogService,
      WorkerPromptFactory workerPromptFactory,
      WorkerTaskRepository workerTaskRepository,
      CodexJsonEventParser codexJsonEventParser,
      CodexDeterministicConfigService codexDeterministicConfigService,
      ContextualToolPolicyService contextualToolPolicyService
  ) {
    this.artifactService = artifactService;
    this.cleanJavaHarnessValidator = cleanJavaHarnessValidator;
    this.gitWorktreeManager = gitWorktreeManager;
    this.orchestrationProperties = orchestrationProperties;
    this.taskPoolService = taskPoolService;
    this.workerLifecycleService = workerLifecycleService;
    this.promptMemoryCaptureService = promptMemoryCaptureService;
    this.repoCatalogService = repoCatalogService;
    this.workerPromptFactory = workerPromptFactory;
    this.workerTaskRepository = workerTaskRepository;
    this.codexJsonEventParser = codexJsonEventParser;
    this.codexDeterministicConfigService = codexDeterministicConfigService;
    this.contextualToolPolicyService = contextualToolPolicyService;
  }

  @Override
  public WorkerExecutionResult executeWorkerTask(WorkerExecutionRequest request) {
    WorkerTask workerTask = workerTaskRepository.getWorkerTask(request.workerTaskId());
    KnownRepo repo = repoCatalogService.requireByPath(request.repoPath().toString());
    Path workspacePath = gitWorktreeManager.prepareWorkspace(request.repoPath(), request.taskId(), request.workerTaskId());
    GitWorktreeManager.GitHeadState initialGitState = gitWorktreeManager.loadHeadState(workspacePath);
    Path outputFile = workspacePath.resolve(".agent-task-manager.last-message.txt");
    List<String> command = buildCommand(
        repo.projectKey(),
        workspacePath,
        outputFile,
        workerPromptFactory.buildPrompt(repo.projectKey(), workerTask)
    );
    workerLifecycleService.submitWorkerCheckIn(
        request.workerTaskId(),
        request.taskId(),
        request.agentId(),
        TaskLifecycleStatus.RUNNING,
        "Worker process started.",
        Map.of("transportKind", WorkerTransportKind.LOCAL_CODEX_EXEC.name(), "workspacePath", workspacePath.toString())
    );

    Process process = start(command, workspacePath);
    ContextualToolPolicyService.ToolPolicyDecision toolPolicyDecision = contextualToolPolicyService.decide(
        "edit",
        workerTask.taskRole() + " " + workerTask.title() + " " + (workerTask.latestSummary() == null ? "" : workerTask.latestSummary()),
        true
    );
    java.util.Set<String> observedToolCalls = new java.util.LinkedHashSet<>();
    String stdout = readStream(process, true, observedToolCalls);
    String stderr = readStream(process, false);
    int exitCode = waitFor(process);
    String finalMessage = readFile(outputFile);
    GitWorktreeManager.GitHeadState finalGitState = gitWorktreeManager.loadHeadState(workspacePath);
    String diff = gitWorktreeManager.captureDiffSince(workspacePath, initialGitState.headCommitHash());
    ContextualToolPolicyService.ToolPolicyAudit toolPolicyAudit = contextualToolPolicyService.audit(
        toolPolicyDecision,
        observedToolCalls,
        finalMessage,
        diff,
        new ContextualToolPolicyService.GitWorkflowEvidence(
            finalGitState.gitRepository(),
            finalGitState.branchName(),
            finalGitState.headCommitHash(),
            finalGitState.headSubject(),
            finalGitState.headBody()
        )
    );
    int effectiveExitCode = toolPolicyAudit.passed() ? exitCode : 97;
    ArtifactRecord outputArtifact = artifactService.writeArtifact(
        request.taskId(),
        request.workerTaskId(),
        "worker-output",
        "Captured worker output",
        stdout
            + (stderr.isBlank() ? "" : "\nSTDERR:\n" + stderr)
            + (toolPolicyAudit.passed()
            ? ""
            : "\nTOOL_POLICY_GATE:\nMissing required tool calls: "
                + formatPolicyItems(toolPolicyAudit.missingCalls())
                + "\nViolations: "
                + formatPolicyItems(toolPolicyAudit.violations())),
        Map.of(
            "exitCode", effectiveExitCode,
            "finalMessage", finalMessage,
            "toolPolicyGatePassed", toolPolicyAudit.passed(),
            "observedToolCalls", toolPolicyAudit.observedCalls(),
            "missingToolCalls", toolPolicyAudit.missingCalls(),
            "toolPolicyViolations", toolPolicyAudit.violations(),
            "gitBranchName", finalGitState.branchName(),
            "gitCommitHash", finalGitState.headCommitHash(),
            "gitCommitSubject", finalGitState.headSubject(),
            "gitCommitBody", finalGitState.headBody()
        )
    );
    ArtifactRecord diffArtifact = artifactService.storeDiffArtifact(request.taskId(), request.workerTaskId(), diff, Map.of("exitCode", effectiveExitCode));

    HarnessApprovalGateResult gateResult = cleanJavaHarnessValidator.runApprovalGate(
        request.taskId(),
        request.workerTaskId(),
        workspacePath,
        diffArtifact.artifactId(),
        effectiveExitCode,
        null,
        null
    );
    TaskLifecycleStatus taskStatus = gateResult.taskStatus();
    String summary = buildSummary(finalMessage, gateResult);
    promptMemoryCaptureService.captureProjectMemory(
        repo.projectKey(),
        request.taskId(),
        request.workerTaskId(),
        "worker-final-response",
        finalMessage.isBlank() ? summary : finalMessage,
        Map.ofEntries(
            Map.entry("repoPath", request.repoPath().toString()),
            Map.entry("exitCode", effectiveExitCode),
            Map.entry("cleanupReviewStatus", gateResult.cleanup().status()),
            Map.entry("validationStatus", gateResult.validation().status()),
            Map.entry("patchScopeAllowed", gateResult.patchScopeAllowed()),
            Map.entry("summary", summary),
            Map.entry("toolPolicyGatePassed", toolPolicyAudit.passed()),
            Map.entry("missingToolCalls", toolPolicyAudit.missingCalls()),
            Map.entry("toolPolicyViolations", toolPolicyAudit.violations()),
            Map.entry("gitBranchName", finalGitState.branchName()),
            Map.entry("gitCommitHash", finalGitState.headCommitHash()),
            Map.entry("gitCommitSubject", finalGitState.headSubject())
        )
    );
    if (taskStatus == TaskLifecycleStatus.COMPLETED) {
      taskPoolService.completeWorkerTask(request.workerTaskId(), summary);
    } else if (taskStatus == TaskLifecycleStatus.NEEDS_REWORK) {
      taskPoolService.markWorkerNeedsRework(request.workerTaskId(), summary);
    } else {
      taskPoolService.failWorkerTask(request.workerTaskId(), summary);
    }

    WorkerRunSummary runSummary = new WorkerRunSummary(
        request.workerTaskId(),
        taskStatus.name(),
        summary,
        diffArtifact.artifactId(),
        java.time.OffsetDateTime.now()
    );
    return new WorkerExecutionResult(
        runSummary,
        outputArtifact.artifactId(),
        diffArtifact.artifactId(),
        gateResult.cleanup().cleanupReviewId(),
        gateResult.validation().reportId(),
        gateResult.patchScopeAllowed(),
        parseCleanupStatus(gateResult.cleanup().status()),
        gateResult.validation().status(),
        effectiveExitCode
    );
  }

  private String buildSummary(String finalMessage, HarnessApprovalGateResult gateResult) {
    String baseSummary = finalMessage.isBlank()
        ? gateResult.summary()
        : finalMessage;
    if (gateResult.summary().equals(baseSummary)) {
      return baseSummary;
    }
    return baseSummary + " " + gateResult.summary();
  }

  private TaskLifecycleStatus parseCleanupStatus(String status) {
    if (status == null || status.isBlank() || "skipped".equalsIgnoreCase(status)) {
      return TaskLifecycleStatus.APPROVED;
    }
    return TaskLifecycleStatus.valueOf(status);
  }

  private List<String> buildCommand(String projectKey, Path workspacePath, Path outputFile, String prompt) {
    List<String> command = new ArrayList<>(ConfiguredCommandResolver.resolveCommand(
        orchestrationProperties.getWorkerCommand()
    ));
    codexDeterministicConfigService.appendDeterministicArguments(command, projectKey);
    command.add("-C");
    command.add(workspacePath.toString());
    command.add("-m");
    command.add(orchestrationProperties.getWorkerModel());
    command.add("-s");
    command.add("workspace-write");
    command.add("exec");
    command.add("--json");
    command.add("--output-last-message");
    command.add(outputFile.toString());
    if (!Files.exists(workspacePath.resolve(".git"))) {
      command.add("--skip-git-repo-check");
    }
    command.add(prompt);
    return command;
  }

  private Process start(List<String> command, Path workspacePath) {
    try {
      return new ProcessBuilder(command)
          .directory(workspacePath.toFile())
          .redirectErrorStream(false)
          .start();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to start worker transport.", exception);
    }
  }

  private String readStream(Process process, boolean stdout) {
    return readStream(process, stdout, java.util.Set.of());
  }

  private String readStream(Process process, boolean stdout, java.util.Set<String> observedToolCalls) {
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
              }
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

  private int waitFor(Process process) {
    try {
      return process.waitFor();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return -1;
    }
  }

  private String readFile(Path path) {
    try {
      return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8).strip() : "";
    } catch (IOException exception) {
      return "";
    }
  }

  private String formatPolicyItems(java.util.Set<String> items) {
    return items == null || items.isEmpty() ? "<none>" : String.join(", ", items);
  }
}
