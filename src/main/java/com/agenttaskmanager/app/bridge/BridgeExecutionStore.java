package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.model.bridge.BridgeClaim;
import com.agenttaskmanager.app.model.bridge.BridgeRunHandle;
import com.agenttaskmanager.app.model.BridgeSessionSummary;
import com.agenttaskmanager.app.persistence.postgres.BridgeSessionRepository;
import com.agenttaskmanager.app.persistence.postgres.PromptMessageRepository;
import com.agenttaskmanager.app.persistence.postgres.PromptRequestRepository;
import com.agenttaskmanager.app.persistence.postgres.PromptRunRepository;
import com.agenttaskmanager.app.persistence.postgres.PromptThreadRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BridgeExecutionStore {

  private final BridgeSessionRepository bridgeSessionRepository;
  private final PromptMessageRepository promptMessageRepository;
  private final PromptRequestRepository promptRequestRepository;
  private final PromptRunRepository promptRunRepository;
  private final PromptThreadRepository promptThreadRepository;

  public BridgeExecutionStore(
      BridgeSessionRepository bridgeSessionRepository,
      PromptMessageRepository promptMessageRepository,
      PromptRequestRepository promptRequestRepository,
      PromptRunRepository promptRunRepository,
      PromptThreadRepository promptThreadRepository
  ) {
    this.bridgeSessionRepository = bridgeSessionRepository;
    this.promptMessageRepository = promptMessageRepository;
    this.promptRequestRepository = promptRequestRepository;
    this.promptRunRepository = promptRunRepository;
    this.promptThreadRepository = promptThreadRepository;
  }

  public void upsertAgentSession(
      String sessionId,
      String agentId,
      String status,
      String hostName,
      String clientName
  ) {
    bridgeSessionRepository.upsertAgentSession(sessionId, agentId, status, hostName, clientName);
  }

  public void upsertBridgeSession(
      String sessionId,
      String agentId,
      String status,
      String hostName,
      String clientName,
      String repoPath,
      String capabilitiesJson
  ) {
    bridgeSessionRepository.upsertBridgeSession(
        sessionId,
        agentId,
        status,
        hostName,
        clientName,
        repoPath,
        capabilitiesJson
    );
  }

  public void heartbeatAgentSession(String sessionId, String status) {
    bridgeSessionRepository.heartbeatAgentSession(sessionId, status);
  }

  public List<BridgeSessionSummary> listBridgeSessions(int limit) {
    return bridgeSessionRepository.listBridgeSessions(limit);
  }

  public Optional<BridgeClaim> claimNextQueued(String agentId, String bridgeTarget) {
    return promptRequestRepository.claimNextQueued(agentId, bridgeTarget);
  }

  public BridgeRunHandle startRun(String requestId, String sessionId, String agentId, String threadKey) {
    return promptRunRepository.startRun(requestId, sessionId, agentId, threadKey);
  }

  public void appendPromptMessage(
      String requestId,
      long runId,
      String messageKind,
      String senderName,
      String body
  ) {
    promptMessageRepository.appendPromptMessage(requestId, runId, messageKind, senderName, body);
  }

  public void completeRun(String requestId, long runId, String summary, String threadSessionId) {
    promptRunRepository.completeRun(requestId, runId, summary, threadSessionId);
  }

  public void failRun(
      String requestId,
      long runId,
      int exitCode,
      String summary,
      String threadSessionId
  ) {
    promptRunRepository.failRun(requestId, runId, exitCode, summary, threadSessionId);
  }

  public void recordThreadSession(String requestId, long runId, String threadSessionId) {
    promptThreadRepository.recordThreadSession(requestId, runId, threadSessionId);
  }

  public void ensurePromptThread(
      String threadKey,
      String projectKey,
      String repoPath,
      String bridgeTarget,
      String requestId
  ) {
    promptThreadRepository.ensurePromptThread(threadKey, projectKey, repoPath, bridgeTarget, requestId);
  }

  public static String normalizeBridgeTarget(String bridgeTarget) {
    return PromptThreadRepository.normalizeBridgeTarget(bridgeTarget);
  }
}
