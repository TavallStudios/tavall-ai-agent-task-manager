package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.bridge.CodexDeterministicConfigService;
import com.agenttaskmanager.app.bridge.CodexJsonEventParser;
import com.agenttaskmanager.app.config.OrchestrationProperties;
import com.agenttaskmanager.app.model.KnownRepo;
import com.agenttaskmanager.app.model.orchestration.ArtifactRecord;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewResult;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewTask;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerExecutionRequest;
import com.agenttaskmanager.app.model.orchestration.WorkerExecutionResult;
import com.agenttaskmanager.app.model.orchestration.WorkerRunSummary;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
import com.agenttaskmanager.app.persistence.postgres.WorkerTaskRepository;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import com.agenttaskmanager.app.model.validation.ValidationReport;
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
  private final CleanupReviewService cleanupReviewService;
  private final GitWorktreeManager gitWorktreeManager;
  private final OrchestrationProperties orchestrationProperties;
  private final TaskPoolService taskPoolService;
  private final ValidationPipelineService validationPipelineService;
  private final WorkerLifecycleService workerLifecycleService;
  private final PromptMemoryCaptureService promptMemoryCaptureService;
  private final com.agenttaskmanager.app.service.RepoCatalogService repoCatalogService;
  private final WorkerPromptFactory workerPromptFactory;
  private final WorkerTaskRepository workerTaskRepository;
  private final CodexJsonEventParser codexJsonEventParser;
  private final CodexDeterministicConfigService codexDeterministicConfigService;

  public LocalCodexWorkerTransport(
      ArtifactService artifactService,
      CleanupReviewService cleanupReviewService,
      GitWorktreeManager gitWorktreeManager,
      OrchestrationProperties orchestrationProperties,
      TaskPoolService taskPoolService,
      ValidationPipelineService validationPipelineService,
      WorkerLifecycleService workerLifecycleService,
      PromptMemoryCaptureService promptMemoryCaptureService,
      com.agenttaskmanager.app.service.RepoCatalogService repoCatalogService,
      WorkerPromptFactory workerPromptFactory,
      WorkerTaskRepository workerTaskRepository,
      CodexJsonEventParser codexJsonEventParser,
      CodexDeterministicConfigService codexDeterministicConfigService
  ) {
    this.artifactService = artifactService;
    this.cleanupReviewService = cleanupReviewService;
    this.gitWorktreeManager = gitWorktreeManager;
    this.orchestrationProperties = orchestrationProperties;
    this.taskPoolService = taskPoolService;
    this.validationPipelineService = validationPipelineService;
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

    CleanupReviewTask cleanupReviewTask = taskPoolService.createCleanupReviewTask(
        request.taskId(),
        request.workerTaskId(),
        diffArtifact.artifactId()
    );
    CleanupReviewResult cleanupReviewResult = cleanupReviewService.runCleanupDiffReview(cleanupReviewTask.cleanupReviewId());
    ValidationReport validationReport = validationPipelineService.runValidationPipeline(
        request.taskId(),
        request.workerTaskId(),
        workspacePath
    );
    boolean patchScopeAllowed = validationPipelineService.validatePatchScope(diff);

    TaskLifecycleStatus taskStatus = resolveTaskStatus(exitCode, validationReport, cleanupReviewResult, patchScopeAllowed);
    String summary = buildSummary(finalMessage, exitCode, validationReport, cleanupReviewResult, patchScopeAllowed);
    promptMemoryCaptureService.captureProjectMemory(
        repo.projectKey(),
        request.taskId(),
        request.workerTaskId(),
        "worker-final-response",
        finalMessage.isBlank() ? summary : finalMessage,
        Map.of(
            "repoPath", request.repoPath().toString(),
            "exitCode", exitCode,
            "cleanupReviewStatus", cleanupReviewResult.status().name(),
            "validationStatus", validationReport.status(),
            "patchScopeAllowed", patchScopeAllowed,
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
        cleanupReviewTask.cleanupReviewId(),
        validationReport.reportId(),
        patchScopeAllowed,
        cleanupReviewResult.status(),
        validationReport.status(),
        exitCode
    );
  }

  private TaskLifecycleStatus resolveTaskStatus(
      int exitCode,
      ValidationReport validationReport,
      CleanupReviewResult cleanupReviewResult,
      boolean patchScopeAllowed
  ) {
    if (exitCode != 0) {
      return TaskLifecycleStatus.FAILED;
    }
    if (!"passed".equals(validationReport.status())) {
      return TaskLifecycleStatus.NEEDS_REWORK;
    }
    if (cleanupReviewResult.status() != TaskLifecycleStatus.APPROVED) {
      return TaskLifecycleStatus.NEEDS_REWORK;
    }
    if (!patchScopeAllowed) {
      return TaskLifecycleStatus.NEEDS_REWORK;
    }
    return TaskLifecycleStatus.COMPLETED;
  }

  private String buildSummary(
      String finalMessage,
      int exitCode,
      ValidationReport validationReport,
      CleanupReviewResult cleanupReviewResult,
      boolean patchScopeAllowed
  ) {
    String baseSummary = finalMessage.isBlank()
        ? "Worker finished with exit code " + exitCode
        : finalMessage;
    List<String> gateNotes = new ArrayList<>();

    if (exitCode != 0) {
      gateNotes.add("worker process failed");
    }
    if (!"passed".equals(validationReport.status())) {
      gateNotes.add("validation did not pass");
    }
    if (cleanupReviewResult.status() != TaskLifecycleStatus.APPROVED) {
      gateNotes.add("cleanup review requires rework");
    }
    if (!patchScopeAllowed) {
      gateNotes.add("patch scope is not allowed");
    }
    if (gateNotes.isEmpty()) {
      return baseSummary;
    }
    return baseSummary + " Gate result: " + String.join("; ", gateNotes) + ".";
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
