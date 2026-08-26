package org.tavall.ai.app.orchestration;

import org.tavall.ai.app.bridge.CodexDeterministicConfigService;
import org.tavall.ai.app.bridge.CodexEventMessage;
import org.tavall.ai.app.cleanjava.CleanJavaHarnessValidator;
import org.tavall.ai.app.config.ConfiguredCommandResolver;
import org.tavall.ai.app.config.OrchestrationProperties;
import org.tavall.ai.app.harness.approval.HarnessApprovalGateResult;
import org.tavall.ai.app.harness.cleanjava.symbol.JavaSymbolBaseline;
import org.tavall.ai.app.harness.cleanjava.symbol.JavaSymbolHarnessService;
import org.tavall.ai.app.harness.cleanjava.symbol.JavaSymbolPostEditResult;
import org.tavall.ai.app.harness.cleanjava.symbol.JavaSymbolRunContext;
import org.tavall.ai.app.model.KnownRepo;
import org.tavall.ai.app.model.orchestration.ArtifactRecord;
import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.model.orchestration.WorkerExecutionRequest;
import org.tavall.ai.app.model.orchestration.WorkerExecutionResult;
import org.tavall.ai.app.model.orchestration.WorkerRunSummary;
import org.tavall.ai.app.model.orchestration.WorkerTask;
import org.tavall.ai.app.model.orchestration.WorkerTransportKind;
import org.tavall.ai.app.persistence.postgres.WorkerTaskRepository;
import org.tavall.ai.app.retrieval.RepoSemanticSyncService;
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
  private final org.tavall.ai.app.service.RepoCatalogService repoCatalogService;
  private final WorkerPromptFactory workerPromptFactory;
  private final WorkerTaskRepository workerTaskRepository;
  private final CodexRunExecutorService codexRunExecutorService;
  private final CodexDeterministicConfigService codexDeterministicConfigService;
  private final ContextualToolPolicyService contextualToolPolicyService;
  private final HarnessMemoryService harnessMemoryService;
  private final JavaSymbolHarnessService javaSymbolHarnessService;
  private final RepoSemanticSyncService repoSemanticSyncService;
  private final WorkerPromptConversationService workerPromptConversationService;

  public LocalCodexWorkerTransport(
      ArtifactService artifactService,
      CleanJavaHarnessValidator cleanJavaHarnessValidator,
      GitWorktreeManager gitWorktreeManager,
      OrchestrationProperties orchestrationProperties,
      TaskPoolService taskPoolService,
      WorkerLifecycleService workerLifecycleService,
      org.tavall.ai.app.service.RepoCatalogService repoCatalogService,
      WorkerPromptFactory workerPromptFactory,
      WorkerTaskRepository workerTaskRepository,
      CodexRunExecutorService codexRunExecutorService,
      CodexDeterministicConfigService codexDeterministicConfigService,
      ContextualToolPolicyService contextualToolPolicyService,
      HarnessMemoryService harnessMemoryService,
      JavaSymbolHarnessService javaSymbolHarnessService,
      RepoSemanticSyncService repoSemanticSyncService,
      WorkerPromptConversationService workerPromptConversationService
  ) {
    this.artifactService = artifactService;
    this.cleanJavaHarnessValidator = cleanJavaHarnessValidator;
    this.gitWorktreeManager = gitWorktreeManager;
    this.orchestrationProperties = orchestrationProperties;
    this.taskPoolService = taskPoolService;
    this.workerLifecycleService = workerLifecycleService;
    this.repoCatalogService = repoCatalogService;
    this.workerPromptFactory = workerPromptFactory;
    this.workerTaskRepository = workerTaskRepository;
    this.codexRunExecutorService = codexRunExecutorService;
    this.codexDeterministicConfigService = codexDeterministicConfigService;
    this.contextualToolPolicyService = contextualToolPolicyService;
    this.harnessMemoryService = harnessMemoryService;
    this.javaSymbolHarnessService = javaSymbolHarnessService;
    this.repoSemanticSyncService = repoSemanticSyncService;
    this.workerPromptConversationService = workerPromptConversationService;
  }

  @Override
  public WorkerExecutionResult executeWorkerTask(WorkerExecutionRequest request) {
    WorkerTask workerTask = workerTaskRepository.getWorkerTask(request.workerTaskId());
    KnownRepo repo = repoCatalogService.requireByPath(request.repoPath().toString());
    Path workspacePath = gitWorktreeManager.prepareWorkspace(request.repoPath(), request.taskId(), request.workerTaskId());
    GitWorktreeManager.GitHeadState initialGitState = gitWorktreeManager.loadHeadState(workspacePath);
    Path outputFile = workspacePath.resolve(".tavall-ai.last-message.txt");
    HarnessMemoryService.MemorySnapshot memorySnapshot = harnessMemoryService.lookupForWorker(repo.projectKey(), workerTask);
    String workerQuery = workerTask.taskRole() + " " + workerTask.title() + " " + (workerTask.latestSummary() == null ? "" : workerTask.latestSummary());
    JavaSymbolBaseline javaSymbolBaseline = javaSymbolHarnessService.captureBaseline(
        request.workerTaskId(),
        request.taskId(),
        request.workerTaskId(),
        repo.projectKey(),
        workspacePath,
        workerQuery,
        initialGitState.headCommitHash(),
        javaHintSourcePaths(workerTask),
        changedJavaSourcePaths(gitWorktreeManager.listWorkspaceChanges(workspacePath))
    );
    JavaSymbolRunContext javaSymbolRunContext = javaSymbolHarnessService.buildRunContext(javaSymbolBaseline);
    String prompt = workerPromptFactory.buildPrompt(
        repo.projectKey(),
        workerTask,
        memorySnapshot.lookupResult().section(),
        javaSymbolRunContext.promptSection()
    );
    ContextualToolPolicyService.ToolPolicyDecision toolPolicyDecision = contextualToolPolicyService.decide(
        "edit",
        workerQuery,
        true,
        true
    );
    WorkerPromptRunHandle promptRunHandle = workerPromptConversationService.startRun(
        repo.projectKey(),
        request.repoPath(),
        request.taskId(),
        request.workerTaskId(),
        request.agentId(),
        request.sessionId(),
        prompt,
        memorySnapshot,
        javaSymbolRunContext,
        toolPolicyDecision
    );
    List<String> command = buildCommand(
        repo.projectKey(),
        workspacePath,
        outputFile,
        prompt
    );
    workerLifecycleService.submitWorkerCheckIn(
        request.workerTaskId(),
        request.taskId(),
        request.agentId(),
        TaskLifecycleStatus.RUNNING,
        "Worker process started.",
        Map.of("transportKind", WorkerTransportKind.LOCAL_CODEX_EXEC.name(), "workspacePath", workspacePath.toString())
    );
    java.util.concurrent.atomic.AtomicReference<WorkerPromptRunHandle> promptRunHandleRef = new java.util.concurrent.atomic.AtomicReference<>(promptRunHandle);
    CodexRunResult runResult = codexRunExecutorService.execute(new CodexRunRequest(
        command,
        workspacePath,
        outputFile,
        initialGitState.headCommitHash(),
        gateFallbackSummary(workerTask),
        toolPolicyDecision,
        new ContextualToolPolicyService.HarnessMemoryEvidence(
            true,
            memorySnapshot.memorySatisfied(),
            memorySnapshot.memoryStatus(),
            memorySnapshot.qdrantHealth()
        ),
        event -> handleEvent(repo, request.repoPath(), workspacePath, promptRunHandleRef, event)
    ));
    Map<String, Object> semanticSyncResult = repoSemanticSyncService.reconcileWorkspaceChanges(
        repo,
        workspacePath,
        initialGitState.headCommitHash()
    );
    workerPromptConversationService.recordSemanticSync(
        promptRunHandleRef.get(),
        repo.projectKey(),
        request.repoPath(),
        semanticSyncResult
    );
    JavaSymbolPostEditResult javaSymbolPostEdit = javaSymbolHarnessService.capturePostEdit(
        request.workerTaskId(),
        request.taskId(),
        request.workerTaskId(),
        repo.projectKey(),
        workspacePath,
        javaSymbolBaseline,
        changedJavaSourcePaths(gitWorktreeManager.listWorkspaceChangesSince(workspacePath, initialGitState.headCommitHash()))
    );
    ContextualToolPolicyService.ToolPolicyAudit toolPolicyAudit = runResult.toolPolicyAudit();
    int effectiveExitCode = runResult.effectiveExitCode();
    ArtifactRecord outputArtifact = artifactService.writeArtifact(
        request.taskId(),
        request.workerTaskId(),
        "worker-output",
        "Captured worker output",
        runResult.stdout()
            + (runResult.stderr().isBlank() ? "" : "\nSTDERR:\n" + runResult.stderr())
            + (toolPolicyAudit.passed()
            ? ""
            : "\nTOOL_POLICY_GATE:\nMissing required tool calls: "
                + formatPolicyItems(toolPolicyAudit.missingCalls())
                + "\nViolations: "
                + formatPolicyItems(toolPolicyAudit.violations())),
        Map.ofEntries(
            Map.entry("exitCode", effectiveExitCode),
            Map.entry("finalMessage", runResult.finalMessage()),
            Map.entry("toolPolicyGatePassed", toolPolicyAudit.passed()),
            Map.entry("observedToolCalls", toolPolicyAudit.observedCalls()),
            Map.entry("missingToolCalls", toolPolicyAudit.missingCalls()),
            Map.entry("toolPolicyViolations", toolPolicyAudit.violations()),
            Map.entry("forbiddenToolCalls", toolPolicyAudit.forbiddenToolCalls()),
            Map.entry("memoryStatus", toolPolicyAudit.memoryStatus()),
            Map.entry("qdrantHealth", toolPolicyAudit.qdrantHealth()),
            Map.entry("runtimePlatform", toolPolicyAudit.runtimePlatform()),
            Map.entry("nativeWindowsShellEnforcementMode", toolPolicyAudit.nativeWindowsShellEnforcementMode()),
            Map.entry("gitWorkflowRequired", toolPolicyAudit.gitWorkflowRequired()),
            Map.entry("gitEnforcementReason", toolPolicyAudit.gitEnforcementReason()),
            Map.entry("diffPresent", toolPolicyAudit.diffPresent()),
            Map.entry("gitCommitCreated", toolPolicyAudit.commitCreated()),
            Map.entry("gitCommitCount", toolPolicyAudit.commitCount()),
            Map.entry("gitBranchName", runResult.finalGitState().branchName()),
            Map.entry("gitCommitHash", runResult.finalGitState().headCommitHash()),
            Map.entry("gitCommitSubject", runResult.finalGitState().headSubject()),
            Map.entry("gitCommitBody", runResult.finalGitState().headBody()),
            Map.entry("javaSymbolStatus", javaSymbolPostEdit.status()),
            Map.entry("reflectionAugmented", javaSymbolPostEdit.reflectionAugmented()),
            Map.entry("contractDeltaStatus", javaSymbolPostEdit.contractDeltaReport().status()),
            Map.entry("contractDeltaSummary", javaSymbolPostEdit.contractDeltaReport().summary()),
            Map.entry("contractDeltaArtifactId", javaSymbolPostEdit.artifactId())
        )
    );
    ArtifactRecord diffArtifact = artifactService.storeDiffArtifact(
        request.taskId(),
        request.workerTaskId(),
        runResult.diffText(),
        Map.of("exitCode", effectiveExitCode)
    );

    HarnessApprovalGateResult gateResult = cleanJavaHarnessValidator.runApprovalGate(
        request.taskId(),
        request.workerTaskId(),
        workspacePath,
        diffArtifact.artifactId(),
        effectiveExitCode,
        null,
        null,
        javaSymbolPostEdit
    );
    TaskLifecycleStatus taskStatus = gateResult.taskStatus();
    workerPromptConversationService.recordGitWorkflow(
        promptRunHandleRef.get(),
        repo.projectKey(),
        request.repoPath(),
        toolPolicyAudit,
        runResult.finalGitState()
    );
    String summary = buildSummary(runResult.finalMessage(), gateResult, toolPolicyAudit, runResult.finalGitState());
    if (taskStatus == TaskLifecycleStatus.COMPLETED) {
      workerPromptConversationService.completeRun(
          promptRunHandleRef.get(),
          repo.projectKey(),
          request.repoPath(),
          runResult.finalMessage(),
          summary
      );
    } else {
      workerPromptConversationService.failRun(
          promptRunHandleRef.get(),
          repo.projectKey(),
          request.repoPath(),
          effectiveExitCode,
          summary
      );
    }
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

  private void handleEvent(
      KnownRepo repo,
      Path repoPath,
      Path workspacePath,
      java.util.concurrent.atomic.AtomicReference<WorkerPromptRunHandle> promptRunHandleRef,
      CodexEventMessage event
  ) {
    promptRunHandleRef.set(
        workerPromptConversationService.recordEvent(promptRunHandleRef.get(), repo.projectKey(), repoPath, event)
    );
    repoSemanticSyncService.syncWorkspaceChanges(repo, workspacePath);
  }

  private String gateFallbackSummary(WorkerTask workerTask) {
    return "Worker run completed for " + workerTask.workerTaskId() + ".";
  }

  private String buildSummary(
      String finalMessage,
      HarnessApprovalGateResult gateResult,
      ContextualToolPolicyService.ToolPolicyAudit toolPolicyAudit,
      GitWorktreeManager.GitHeadState gitHeadState
  ) {
    String baseSummary = finalMessage.isBlank()
        ? gateResult.summary()
        : finalMessage;
    String summary = gateResult.summary().equals(baseSummary)
        ? baseSummary
        : baseSummary + " " + gateResult.summary();
    return appendGitSummary(summary, toolPolicyAudit, gitHeadState);
  }

  private String appendGitSummary(
      String summary,
      ContextualToolPolicyService.ToolPolicyAudit toolPolicyAudit,
      GitWorktreeManager.GitHeadState gitHeadState
  ) {
    if (!toolPolicyAudit.gitWorkflowRequired()) {
      return summary;
    }
    if (!toolPolicyAudit.commitCreated()) {
      return summary + " Git workflow did not create a new commit for this prompt.";
    }
    return summary + " Git workflow created "
        + toolPolicyAudit.commitCount()
        + " commit(s) on "
        + blank(gitHeadState.branchName())
        + " ending at "
        + blank(gitHeadState.headCommitHash())
        + ".";
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

  private String formatPolicyItems(java.util.Set<String> items) {
    return items == null || items.isEmpty() ? "<none>" : String.join(", ", items);
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }

  private List<String> javaHintSourcePaths(WorkerTask workerTask) {
    Object changedFiles = workerTask.metadata().get("changedFiles");
    if (changedFiles instanceof Iterable<?> values) {
      List<String> paths = new ArrayList<>();
      for (Object value : values) {
        if (value != null) {
          paths.add(String.valueOf(value));
        }
      }
      return paths;
    }
    return List.of();
  }

  private List<String> changedJavaSourcePaths(List<GitWorktreeManager.WorkspaceFileChange> changes) {
    return changes.stream()
        .map(GitWorktreeManager.WorkspaceFileChange::relativePath)
        .filter(path -> path != null && path.endsWith(".java"))
        .sorted()
        .toList();
  }
}
