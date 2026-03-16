package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.bridge.CodexDeterministicConfigService;
import com.agenttaskmanager.app.bridge.CodexJsonEventParser;
import com.agenttaskmanager.app.config.OrchestrationProperties;
import com.agenttaskmanager.app.harness.approval.HarnessApprovalGateResult;
import com.agenttaskmanager.app.harness.approval.HarnessApprovalService;
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
  private final GitWorktreeManager gitWorktreeManager;
  private final HarnessApprovalService harnessApprovalService;
  private final OrchestrationProperties orchestrationProperties;
  private final TaskPoolService taskPoolService;
  private final WorkerLifecycleService workerLifecycleService;
  private final PromptMemoryCaptureService promptMemoryCaptureService;
  private final com.agenttaskmanager.app.service.RepoCatalogService repoCatalogService;
  private final WorkerPromptFactory workerPromptFactory;
  private final WorkerTaskRepository workerTaskRepository;
  private final CodexJsonEventParser codexJsonEventParser;
  private final CodexDeterministicConfigService codexDeterministicConfigService;

  public LocalCodexWorkerTransport(
      ArtifactService artifactService,
      GitWorktreeManager gitWorktreeManager,
      HarnessApprovalService harnessApprovalService,
      OrchestrationProperties orchestrationProperties,
      TaskPoolService taskPoolService,
      WorkerLifecycleService workerLifecycleService,
      PromptMemoryCaptureService promptMemoryCaptureService,
      com.agenttaskmanager.app.service.RepoCatalogService repoCatalogService,
      WorkerPromptFactory workerPromptFactory,
      WorkerTaskRepository workerTaskRepository,
      CodexJsonEventParser codexJsonEventParser,
      CodexDeterministicConfigService codexDeterministicConfigService
  ) {
    this.artifactService = artifactService;
    this.gitWorktreeManager = gitWorktreeManager;
    this.harnessApprovalService = harnessApprovalService;
    this.orchestrationProperties = orchestrationProperties;
    this.taskPoolService = taskPoolService;
    this.workerLifecycleService = workerLifecycleService;
    this.promptMemoryCaptureService = promptMemoryCaptureService;
    this.repoCatalogService = repoCatalogService;
    this.workerPromptFactory = workerPromptFactory;
    this.workerTaskRepository = workerTaskRepository;
    this.codexJsonEventParser = codexJsonEventParser;
    this.codexDeterministicConfigService = codexDeterministicConfigService;
  }

  @Override
  public WorkerExecutionResult executeWorkerTask(WorkerExecutionRequest request) {
    WorkerTask workerTask = workerTaskRepository.getWorkerTask(request.workerTaskId());
    KnownRepo repo = repoCatalogService.requireByPath(request.repoPath().toString());
    Path workspacePath = gitWorktreeManager.prepareWorkspace(request.repoPath(), request.taskId(), request.workerTaskId());
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
    String stdout = readStream(process, true);
    String stderr = readStream(process, false);
    int exitCode = waitFor(process);

    String finalMessage = readFile(outputFile);
    String diff = gitWorktreeManager.captureDiff(workspacePath);
    ArtifactRecord outputArtifact = artifactService.writeArtifact(
        request.taskId(),
        request.workerTaskId(),
        "worker-output",
        "Captured worker output",
        stdout + (stderr.isBlank() ? "" : "\nSTDERR:\n" + stderr),
        Map.of("exitCode", exitCode, "finalMessage", finalMessage)
    );
    ArtifactRecord diffArtifact = artifactService.storeDiffArtifact(request.taskId(), request.workerTaskId(), diff, Map.of("exitCode", exitCode));

    HarnessApprovalGateResult gateResult = harnessApprovalService.runApprovalGate(
        request.taskId(),
        request.workerTaskId(),
        workspacePath
            ,
        diffArtifact.artifactId(),
        exitCode,
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
        Map.of(
            "repoPath", request.repoPath().toString(),
            "exitCode", exitCode,
            "cleanupReviewStatus", gateResult.cleanup().status(),
            "validationStatus", gateResult.validation().status(),
            "patchScopeAllowed", gateResult.patchScopeAllowed(),
            "summary", summary
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
        exitCode
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
    List<String> command = new ArrayList<>();
    command.add(orchestrationProperties.getWorkerCommand());
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
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        stdout ? process.getInputStream() : process.getErrorStream(),
        StandardCharsets.UTF_8
    ))) {
      StringBuilder output = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        if (stdout) {
          codexJsonEventParser.parseLine(line);
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
}
