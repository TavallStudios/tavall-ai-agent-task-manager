package org.tavall.ai.app.orchestration;

import org.tavall.ai.app.bridge.CodexEventMessage;
import org.tavall.ai.app.memory.MemoryRuntimeService;
import org.tavall.ai.app.memory.MemoryTurnHandle;
import org.tavall.ai.app.model.PromptRequestSummary;
import org.tavall.ai.app.model.bridge.BridgeRunHandle;
import org.tavall.ai.app.persistence.postgres.PromptMessageRepository;
import org.tavall.ai.app.persistence.postgres.PromptRunRepository;
import org.tavall.ai.app.service.PromptRequestService;
import org.tavall.ai.app.harness.cleanjava.symbol.JavaSymbolRunContext;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkerPromptConversationService {

  private final PromptMessageRepository promptMessageRepository;
  private final PromptRequestService promptRequestService;
  private final PromptRunRepository promptRunRepository;
  private final MemoryRuntimeService memoryRuntimeService;
  private final HarnessTranscriptService harnessTranscriptService;

  public WorkerPromptConversationService(
      PromptMessageRepository promptMessageRepository,
      PromptRequestService promptRequestService,
      PromptRunRepository promptRunRepository,
      MemoryRuntimeService memoryRuntimeService,
      HarnessTranscriptService harnessTranscriptService
  ) {
    this.promptMessageRepository = promptMessageRepository;
    this.promptRequestService = promptRequestService;
    this.promptRunRepository = promptRunRepository;
    this.memoryRuntimeService = memoryRuntimeService;
    this.harnessTranscriptService = harnessTranscriptService;
  }

  public WorkerPromptRunHandle startRun(
      String projectKey,
      Path repoPath,
      String taskId,
      String workerTaskId,
      String agentId,
      String sessionId,
      String promptEnvelope,
      HarnessMemoryService.MemorySnapshot memorySnapshot,
      JavaSymbolRunContext javaSymbolRunContext,
      ContextualToolPolicyService.ToolPolicyDecision toolPolicyDecision
  ) {
    String threadKey = "worker-task:" + workerTaskId;
    PromptRequestSummary request = promptRequestService.createWorkerExecutionRequest(
        projectKey,
        repoPath.toString(),
        threadKey,
        promptEnvelope,
        agentId,
        sessionId,
        taskId,
        workerTaskId
    );
    MemoryTurnHandle memoryTurnHandle = memoryRuntimeService.beginTurn(
        request.requestId(),
        projectKey,
        threadKey,
        sessionId,
        agentId,
        "local-codex-worker",
        repoPath.toString(),
        promptEnvelope,
        promptEnvelope,
        Map.of(
            "taskId", taskId == null ? "" : taskId,
            "workerTaskId", workerTaskId == null ? "" : workerTaskId,
            "threadKey", threadKey,
            "projectKey", projectKey
        )
    );
    BridgeRunHandle runHandle = promptRunRepository.startRun(request.requestId(), sessionId, agentId, threadKey);
    WorkerPromptRunHandle handle = new WorkerPromptRunHandle(
        request.requestId(),
        runHandle.runId(),
        threadKey,
        "",
        memoryTurnHandle
    );
    recordMessage(
        handle,
        projectKey,
        repoPath,
        "worker-harness-bootstrap",
        "tavall-ai",
        harnessTranscriptService.bootstrapSummary(
            memorySnapshot.memoryStatus(),
            memorySnapshot.qdrantHealth(),
            toolPolicyDecision,
            javaSymbolRunContext
        ),
        Map.of(
            "memoryStatus", memorySnapshot.memoryStatus(),
            "qdrantHealth", memorySnapshot.qdrantHealth(),
            "javaSymbolStatus", javaSymbolRunContext == null ? "" : javaSymbolRunContext.status(),
            "repoBackedWriteRun", toolPolicyDecision.repoBackedWriteRun()
        )
    );
    recordMessage(
        handle,
        projectKey,
        repoPath,
        "worker-memory-lookup",
        "qdrant-memory",
        memoryTurnHandle.hydration().summary(),
        Map.of(
            "memoryStatus", memorySnapshot.memoryStatus(),
            "qdrantHealth", memorySnapshot.qdrantHealth(),
            "memorySatisfied", memorySnapshot.memorySatisfied(),
            "exactCount", memoryTurnHandle.hydration().exactRecords().size(),
            "semanticCount", memoryTurnHandle.hydration().semanticCandidates().size()
        )
    );
    if (javaSymbolRunContext != null) {
      recordMessage(
          handle,
          projectKey,
          repoPath,
          "worker-java-symbol-context",
          "java-symbol-harness",
          javaSymbolRunContext.summary(),
          Map.of(
              "javaSymbolStatus", javaSymbolRunContext.status(),
              "reflectionAugmented", javaSymbolRunContext.reflectionAugmented(),
              "targetedClasses", javaSymbolRunContext.targetedClasses()
          )
      );
    }
    recordMessage(
        handle,
        projectKey,
        repoPath,
        "worker-tool-policy",
        "tavall-ai",
        harnessTranscriptService.toolPolicySummary(toolPolicyDecision),
        Map.of(
            "requiredCalls", toolPolicyDecision.requiredCalls(),
            "rationale", toolPolicyDecision.rationale(),
            "gitEnforcementScope", toolPolicyDecision.gitEnforcementScope(),
            "nativeWindowsShellEnforcementMode", toolPolicyDecision.nativeWindowsShellEnforcementMode()
        )
    );
    return handle;
  }

  public WorkerPromptRunHandle recordEvent(
      WorkerPromptRunHandle handle,
      String projectKey,
      Path repoPath,
      CodexEventMessage eventMessage
  ) {
    WorkerPromptRunHandle updatedHandle = handle;
    if ("thread-started".equals(eventMessage.kind())) {
      String threadSessionId = extractThreadSessionId(eventMessage.body());
      promptRunRepository.attachThreadSession(handle.requestId(), handle.runId(), threadSessionId);
      updatedHandle = handle.withThreadSessionId(threadSessionId);
    }
    String body = "tool-call".equals(eventMessage.kind())
        ? harnessTranscriptService.toolCallSummary(eventMessage.body())
        : eventMessage.body();
    recordMessage(
        updatedHandle,
        projectKey,
        repoPath,
        "codex-" + eventMessage.kind(),
        eventMessage.sender(),
        body,
        "tool-call".equals(eventMessage.kind())
            ? Map.of(
                "threadSessionId", updatedHandle.threadSessionId(),
                "toolCallSignature", eventMessage.body(),
                "toolName", eventMessage.toolName()
            )
            : Map.of("threadSessionId", updatedHandle.threadSessionId())
    );
    return updatedHandle;
  }

  public void recordSemanticSync(
      WorkerPromptRunHandle handle,
      String projectKey,
      Path repoPath,
      Map<String, Object> syncResult
  ) {
    recordMessage(
        handle,
        projectKey,
        repoPath,
        "worker-semantic-sync",
        "tavall-ai",
        harnessTranscriptService.semanticSyncSummary(syncResult),
        syncResult
    );
  }

  public void recordGitWorkflow(
      WorkerPromptRunHandle handle,
      String projectKey,
      Path repoPath,
      ContextualToolPolicyService.ToolPolicyAudit toolPolicyAudit,
      GitWorktreeManager.GitHeadState gitHeadState
  ) {
    recordMessage(
        handle,
        projectKey,
        repoPath,
        "worker-git-workflow",
        "tavall-ai",
        harnessTranscriptService.gitWorkflowSummary(toolPolicyAudit, gitHeadState),
        Map.of(
            "gitWorkflowRequired", toolPolicyAudit.gitWorkflowRequired(),
            "gitCommitCreated", toolPolicyAudit.commitCreated(),
            "gitCommitCount", toolPolicyAudit.commitCount(),
            "gitBranchName", gitHeadState.branchName(),
            "gitCommitHash", gitHeadState.headCommitHash(),
            "gitEnforcementReason", toolPolicyAudit.gitEnforcementReason()
        )
    );
  }

  public void completeRun(
      WorkerPromptRunHandle handle,
      String projectKey,
      Path repoPath,
      String finalMessage,
      String summary
  ) {
    String effectiveBody = finalMessage == null || finalMessage.isBlank() ? summary : finalMessage.strip();
    recordMessage(handle, projectKey, repoPath, "worker-final-response", "codex", effectiveBody, Map.of());
    promptRunRepository.completeRun(handle.requestId(), handle.runId(), summary, handle.threadSessionId());
    memoryRuntimeService.completeTurn(handle.memoryTurnHandle(), effectiveBody, false);
  }

  public void failRun(
      WorkerPromptRunHandle handle,
      String projectKey,
      Path repoPath,
      int exitCode,
      String summary
  ) {
    recordMessage(handle, projectKey, repoPath, "worker-run-failure", "codex", summary, Map.of("exitCode", exitCode));
    promptRunRepository.failRun(handle.requestId(), handle.runId(), exitCode, summary, handle.threadSessionId());
    memoryRuntimeService.completeTurn(handle.memoryTurnHandle(), summary, true);
  }

  private void recordMessage(
      WorkerPromptRunHandle handle,
      String projectKey,
      Path repoPath,
      String kind,
      String sender,
      String body,
      Map<String, Object> metadata
  ) {
    promptMessageRepository.appendPromptMessage(handle.requestId(), handle.runId(), kind, sender, body, metadata);
  }

  private String extractThreadSessionId(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    String prefix = "Started thread ";
    return body.startsWith(prefix) ? body.substring(prefix.length()).strip() : body.strip();
  }
}
