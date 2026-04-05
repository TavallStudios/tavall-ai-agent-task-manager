package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.config.CodexBridgeProperties;
import com.agenttaskmanager.app.harness.cleanjava.symbol.JavaSymbolBaseline;
import com.agenttaskmanager.app.harness.cleanjava.symbol.JavaSymbolHarnessService;
import com.agenttaskmanager.app.harness.cleanjava.symbol.JavaSymbolPostEditResult;
import com.agenttaskmanager.app.harness.cleanjava.symbol.JavaSymbolRunContext;
import com.agenttaskmanager.app.model.PromptThreadMemoryLookupResult;
import com.agenttaskmanager.app.model.bridge.BridgeClaim;
import com.agenttaskmanager.app.model.bridge.BridgeRunHandle;
import com.agenttaskmanager.app.orchestration.CodexRunExecutorService;
import com.agenttaskmanager.app.orchestration.CodexRunRequest;
import com.agenttaskmanager.app.orchestration.CodexRunResult;
import com.agenttaskmanager.app.orchestration.ContextualToolPolicyService;
import com.agenttaskmanager.app.orchestration.GitWorktreeManager;
import com.agenttaskmanager.app.orchestration.HarnessTranscriptService;
import com.agenttaskmanager.app.retrieval.RepoSemanticSyncService;
import com.agenttaskmanager.app.persistence.postgres.BridgeSessionRepository;
import com.agenttaskmanager.app.persistence.postgres.PromptMessageRepository;
import com.agenttaskmanager.app.persistence.postgres.PromptRequestRepository;
import com.agenttaskmanager.app.persistence.postgres.PromptRunRepository;
import com.agenttaskmanager.app.service.PromptThreadMemoryService;
import com.agenttaskmanager.app.service.RepoCatalogService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class RemoteHeadlessPromptExecutionService {

  public static final String BRIDGE_TARGET = "remote-headless";

  private final CodexBridgeProperties bridgeProperties;
  private final CodexExecCommandFactory commandFactory;
  private final CodexRunExecutorService codexRunExecutorService;
  private final ContextualToolPolicyService contextualToolPolicyService;
  private final GitWorktreeManager gitWorktreeManager;
  private final HarnessTranscriptService harnessTranscriptService;
  private final JavaSymbolHarnessService javaSymbolHarnessService;
  private final BridgeSessionRepository bridgeSessionRepository;
  private final PromptMessageRepository promptMessageRepository;
  private final PromptRequestRepository promptRequestRepository;
  private final PromptRunRepository promptRunRepository;
  private final PromptThreadMemoryService promptThreadMemoryService;
  private final RepoCatalogService repoCatalogService;
  private final RepoSemanticSyncService repoSemanticSyncService;

  public RemoteHeadlessPromptExecutionService(
      CodexBridgeProperties bridgeProperties,
      CodexExecCommandFactory commandFactory,
      CodexRunExecutorService codexRunExecutorService,
      ContextualToolPolicyService contextualToolPolicyService,
      GitWorktreeManager gitWorktreeManager,
      HarnessTranscriptService harnessTranscriptService,
      JavaSymbolHarnessService javaSymbolHarnessService,
      BridgeSessionRepository bridgeSessionRepository,
      PromptMessageRepository promptMessageRepository,
      PromptRequestRepository promptRequestRepository,
      PromptRunRepository promptRunRepository,
      PromptThreadMemoryService promptThreadMemoryService,
      RepoCatalogService repoCatalogService,
      RepoSemanticSyncService repoSemanticSyncService
  ) {
    this.bridgeProperties = bridgeProperties;
    this.commandFactory = commandFactory;
    this.codexRunExecutorService = codexRunExecutorService;
    this.contextualToolPolicyService = contextualToolPolicyService;
    this.gitWorktreeManager = gitWorktreeManager;
    this.harnessTranscriptService = harnessTranscriptService;
    this.javaSymbolHarnessService = javaSymbolHarnessService;
    this.bridgeSessionRepository = bridgeSessionRepository;
    this.promptMessageRepository = promptMessageRepository;
    this.promptRequestRepository = promptRequestRepository;
    this.promptRunRepository = promptRunRepository;
    this.promptThreadMemoryService = promptThreadMemoryService;
    this.repoCatalogService = repoCatalogService;
    this.repoSemanticSyncService = repoSemanticSyncService;
  }

  public Optional<CodexRunResult> executeNextQueued() {
    if (!bridgeProperties.isEnabled() || bridgeProperties.getAgentId() == null || bridgeProperties.getAgentId().isBlank()) {
      return Optional.empty();
    }
    return promptRequestRepository.claimNextQueued(bridgeProperties.getAgentId(), BRIDGE_TARGET)
        .map(this::executeClaim);
  }

  public CodexRunResult executeClaim(BridgeClaim claim) {
    Path workspacePath = requireWorkspace(claim.repoPath());
    com.agenttaskmanager.app.model.KnownRepo repo = repoCatalogService.requireByPath(claim.repoPath());
    boolean repoBackedWriteRun = isRepoBackedWriteRun(claim.executionMode(), workspacePath);
    ContextualToolPolicyService.ToolPolicyDecision toolPolicyDecision = contextualToolPolicyService.decide(
        claim.executionMode(),
        claim.promptText(),
        false,
        repoBackedWriteRun
    );
    JavaSymbolBaseline javaSymbolBaseline = javaSymbolHarnessService.captureBaseline(
        claim.requestId(),
        "",
        "",
        claim.projectKey(),
        workspacePath,
        claim.promptText(),
        gitWorktreeManager.loadHeadState(workspacePath).headCommitHash(),
        List.of(),
        changedJavaSourcePaths(gitWorktreeManager.listWorkspaceChanges(workspacePath))
    );
    JavaSymbolRunContext javaSymbolRunContext = javaSymbolHarnessService.buildRunContext(javaSymbolBaseline);
    PromptThreadMemoryLookupResult memoryLookup = promptThreadMemoryService.lookup(
        claim.projectKey(),
        claim.threadKey(),
        claim.promptText()
    );
    String promptEnvelope = commandFactory.buildPromptEnvelope(
        claim.executionMode(),
        claim.promptText(),
        memoryLookup.section(),
        javaSymbolRunContext.promptSection(),
        repoBackedWriteRun
    );
    String agentSessionId = resolveAgentSessionId(claim);
    BridgeRunHandle runHandle = promptRunRepository.startRun(
        claim.requestId(),
        agentSessionId,
        bridgeProperties.getAgentId(),
        claim.threadKey()
    );
    appendMessage(claim, runHandle.runId(), "bridge-memory-lookup", "qdrant-memory", memoryLookup.summary(), Map.of(
        "memorySection", memoryLookup.section()
    ));
    appendMessage(
        claim,
        runHandle.runId(),
        "bridge-harness-bootstrap",
        "agent-task-manager",
        harnessTranscriptService.bootstrapSummary("active", "unknown", toolPolicyDecision, javaSymbolRunContext),
        Map.of(
            "repoBackedWriteRun", toolPolicyDecision.repoBackedWriteRun(),
            "javaSymbolStatus", javaSymbolRunContext.status()
        )
    );
    appendMessage(claim, runHandle.runId(), "bridge-java-symbol-context", "java-symbol-harness", javaSymbolRunContext.summary(), Map.of(
        "javaSymbolStatus", javaSymbolRunContext.status(),
        "reflectionAugmented", javaSymbolRunContext.reflectionAugmented(),
        "targetedClasses", javaSymbolRunContext.targetedClasses()
    ));
    appendMessage(
        claim,
        runHandle.runId(),
        "bridge-tool-policy",
        "agent-task-manager",
        harnessTranscriptService.toolPolicySummary(toolPolicyDecision),
        Map.of(
            "requiredCalls", toolPolicyDecision.requiredCalls(),
            "rationale", toolPolicyDecision.rationale(),
            "gitEnforcementScope", toolPolicyDecision.gitEnforcementScope(),
            "nativeWindowsShellEnforcementMode", toolPolicyDecision.nativeWindowsShellEnforcementMode()
        )
    );
    AtomicReference<String> threadSessionId = new AtomicReference<>(blank(claim.resumeSessionId()));
    GitWorktreeManager.GitHeadState initialGitState = gitWorktreeManager.loadHeadState(workspacePath);
    CodexRunResult result = codexRunExecutorService.execute(new CodexRunRequest(
        commandFactory.buildCommand(
            claim.projectKey(),
            workspacePath,
            claim.executionMode(),
            workspacePath.resolve(".agent-task-manager.prompt-bridge.last-message.txt"),
            claim.resumeSessionId(),
            promptEnvelope
        ),
        workspacePath,
        workspacePath.resolve(".agent-task-manager.prompt-bridge.last-message.txt"),
        initialGitState.headCommitHash(),
        "Remote headless prompt bridge completed.",
        toolPolicyDecision,
        ContextualToolPolicyService.HarnessMemoryEvidence.disabled(),
        event -> recordEvent(claim, repo, workspacePath, runHandle.runId(), threadSessionId, event)
    ));
    Map<String, Object> semanticSyncResult = repoSemanticSyncService.reconcileWorkspaceChanges(
        repo,
        workspacePath,
        initialGitState.headCommitHash()
    );
    appendMessage(
        claim,
        runHandle.runId(),
        "bridge-semantic-sync",
        "agent-task-manager",
        harnessTranscriptService.semanticSyncSummary(semanticSyncResult),
        semanticSyncResult
    );
    JavaSymbolPostEditResult javaSymbolPostEdit = javaSymbolHarnessService.capturePostEdit(
        claim.requestId(),
        "",
        "",
        claim.projectKey(),
        workspacePath,
        javaSymbolBaseline,
        changedJavaSourcePaths(gitWorktreeManager.listWorkspaceChangesSince(workspacePath, initialGitState.headCommitHash()))
    );
    CodexRunResult effectiveResult = applyJavaSymbolGate(result, javaSymbolPostEdit);
    appendMessage(
        claim,
        runHandle.runId(),
        "bridge-git-workflow",
        "agent-task-manager",
        harnessTranscriptService.gitWorkflowSummary(effectiveResult.toolPolicyAudit(), effectiveResult.finalGitState()),
        Map.of(
            "gitWorkflowRequired", effectiveResult.toolPolicyAudit().gitWorkflowRequired(),
            "gitCommitCreated", effectiveResult.toolPolicyAudit().commitCreated(),
            "gitCommitCount", effectiveResult.toolPolicyAudit().commitCount(),
            "gitBranchName", effectiveResult.finalGitState().branchName(),
            "gitCommitHash", effectiveResult.finalGitState().headCommitHash(),
            "gitEnforcementReason", effectiveResult.toolPolicyAudit().gitEnforcementReason()
        )
    );
    String summary = summarize(effectiveResult, javaSymbolPostEdit);
    if (effectiveResult.effectiveExitCode() == 0) {
      appendMessage(claim, runHandle.runId(), "bridge-final-response", "codex", effectiveResult.finalMessage(), resultMetadata(effectiveResult, javaSymbolPostEdit));
      promptRunRepository.completeRun(claim.requestId(), runHandle.runId(), summary, threadSessionId.get());
    } else {
      appendMessage(claim, runHandle.runId(), "bridge-run-failure", "codex", summary, resultMetadata(effectiveResult, javaSymbolPostEdit));
      promptRunRepository.failRun(claim.requestId(), runHandle.runId(), effectiveResult.effectiveExitCode(), summary, threadSessionId.get());
    }
    promptThreadMemoryService.capturePromptThreadSnapshot(claim.projectKey(), claim.threadKey());
    return effectiveResult;
  }

  private void recordEvent(
      BridgeClaim claim,
      com.agenttaskmanager.app.model.KnownRepo repo,
      Path workspacePath,
      long runId,
      AtomicReference<String> threadSessionId,
      CodexEventMessage event
  ) {
    if ("thread-started".equals(event.kind())) {
      String sessionId = extractThreadSessionId(event.body());
      promptRunRepository.attachThreadSession(claim.requestId(), runId, sessionId);
      threadSessionId.set(sessionId);
    }
    repoSemanticSyncService.syncWorkspaceChanges(repo, workspacePath);
    appendMessage(
        claim,
        runId,
        "codex-" + event.kind(),
        event.sender(),
        "tool-call".equals(event.kind())
            ? harnessTranscriptService.toolCallSummary(event.body())
            : event.body(),
        "tool-call".equals(event.kind())
            ? Map.of(
                "threadSessionId", threadSessionId.get(),
                "toolCallSignature", event.body(),
                "toolName", event.toolName()
            )
            : Map.of("threadSessionId", threadSessionId.get())
    );
  }

  private void appendMessage(
      BridgeClaim claim,
      long runId,
      String kind,
      String sender,
      String body,
      Map<String, Object> metadata
  ) {
    promptMessageRepository.appendPromptMessage(claim.requestId(), runId, kind, sender, body, metadata);
    promptThreadMemoryService.capturePromptThreadMessage(
        claim.projectKey(),
        claim.requestId(),
        claim.threadKey(),
        claim.repoPath(),
        BRIDGE_TARGET,
        kind,
        body,
        metadata
    );
  }

  private Map<String, Object> resultMetadata(CodexRunResult result, JavaSymbolPostEditResult javaSymbolPostEdit) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("exitCode", result.effectiveExitCode());
    metadata.put("toolPolicyGatePassed", result.toolPolicyAudit().passed());
    metadata.put("observedToolCalls", result.observedToolCalls());
    metadata.put("missingToolCalls", result.toolPolicyAudit().missingCalls());
    metadata.put("toolPolicyViolations", result.toolPolicyAudit().violations());
    metadata.put("forbiddenToolCalls", result.toolPolicyAudit().forbiddenToolCalls());
    metadata.put("runtimePlatform", result.toolPolicyAudit().runtimePlatform());
    metadata.put("nativeWindowsShellEnforcementMode", result.toolPolicyAudit().nativeWindowsShellEnforcementMode());
    metadata.put("gitWorkflowRequired", result.toolPolicyAudit().gitWorkflowRequired());
    metadata.put("gitEnforcementReason", result.toolPolicyAudit().gitEnforcementReason());
    metadata.put("diffPresent", result.diffPresent());
    metadata.put("gitCommitCreated", result.toolPolicyAudit().commitCreated());
    metadata.put("gitCommitCount", result.toolPolicyAudit().commitCount());
    metadata.put("gitBranchName", result.finalGitState().branchName());
    metadata.put("gitCommitHash", result.finalGitState().headCommitHash());
    metadata.put("gitCommitSubject", result.finalGitState().headSubject());
    metadata.put("javaSymbolStatus", javaSymbolPostEdit.status());
    metadata.put("reflectionAugmented", javaSymbolPostEdit.reflectionAugmented());
    metadata.put("contractDeltaStatus", javaSymbolPostEdit.contractDeltaReport().status());
    metadata.put("contractDeltaSummary", javaSymbolPostEdit.contractDeltaReport().summary());
    metadata.put("contractDeltaArtifactId", javaSymbolPostEdit.artifactId());
    return metadata;
  }

  private String summarize(CodexRunResult result, JavaSymbolPostEditResult javaSymbolPostEdit) {
    if (result.effectiveExitCode() == 0) {
      String baseSummary = result.finalMessage().isBlank() ? "Remote headless prompt bridge completed." : result.finalMessage();
      return appendGitSummary(baseSummary, result);
    }
    if ("failed".equalsIgnoreCase(javaSymbolPostEdit.contractDeltaReport().status())) {
      return "Java symbol gate failed: " + javaSymbolPostEdit.contractDeltaReport().summary();
    }
    if (result.toolPolicyAudit().passed()) {
      return "Remote headless prompt bridge failed with exit code " + result.exitCode() + ".";
    }
    return "Tool policy gate failed: missing="
        + String.join(", ", result.toolPolicyAudit().missingCalls())
        + " violations="
        + String.join(", ", result.toolPolicyAudit().violations());
  }

  private String appendGitSummary(String summary, CodexRunResult result) {
    if (!result.toolPolicyAudit().gitWorkflowRequired()) {
      return summary;
    }
    if (!result.toolPolicyAudit().commitCreated()) {
      return summary + " Git workflow did not create a new commit for this prompt.";
    }
    return summary + " Git workflow created "
        + result.toolPolicyAudit().commitCount()
        + " commit(s) on "
        + blank(result.finalGitState().branchName())
        + " ending at "
        + blank(result.finalGitState().headCommitHash())
        + ".";
  }

  private CodexRunResult applyJavaSymbolGate(CodexRunResult result, JavaSymbolPostEditResult javaSymbolPostEdit) {
    if (!"failed".equalsIgnoreCase(javaSymbolPostEdit.contractDeltaReport().status())) {
      return result;
    }
    return new CodexRunResult(
        result.stdout(),
        result.stderr(),
        result.exitCode(),
        98,
        result.finalMessage(),
        result.observedToolCalls(),
        result.diffText(),
        result.diffPresent(),
        result.finalGitState(),
        result.toolPolicyAudit()
    );
  }

  private Path requireWorkspace(String repoPath) {
    if (repoPath == null || repoPath.isBlank()) {
      throw new IllegalArgumentException("repoPath is required for remote headless prompt execution.");
    }
    return Path.of(repoPath).toAbsolutePath().normalize();
  }

  private boolean isRepoBackedWriteRun(String executionMode, Path workspacePath) {
    return !"read-only".equalsIgnoreCase(executionMode) && gitWorktreeManager.isGitRepository(workspacePath);
  }

  private String extractThreadSessionId(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    String prefix = "Started thread ";
    return body.startsWith(prefix) ? body.substring(prefix.length()).strip() : body.strip();
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }

  private String resolveAgentSessionId(BridgeClaim claim) {
    String sessionId = blank(claim.resumeSessionId());
    if (sessionId.isBlank()) {
      sessionId = "bridge-session-" + claim.requestId();
    }
    bridgeSessionRepository.upsertAgentSession(
        sessionId,
        bridgeProperties.getAgentId(),
        "running",
        "localhost",
        BRIDGE_TARGET
    );
    return sessionId;
  }

  private List<String> changedJavaSourcePaths(List<GitWorktreeManager.WorkspaceFileChange> changes) {
    return changes.stream()
        .map(GitWorktreeManager.WorkspaceFileChange::relativePath)
        .filter(path -> path != null && path.endsWith(".java"))
        .sorted()
        .toList();
  }
}
