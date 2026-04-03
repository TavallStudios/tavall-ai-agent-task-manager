package com.agenttaskmanager.app.service.session;

import com.agenttaskmanager.app.config.CodexClientPlatformProperties;
import com.agenttaskmanager.app.model.session.CodexRuntimeModels.RuntimeEventPublishRequest;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.FileFocusRequest;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.OutputReleaseState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.OutputSnapshot;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.PatchArtifact;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeConnection;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeConnectionState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeLease;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeLeaseState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SessionLifecycleState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.ToolReceipt;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.TurnSummary;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.VerifierResult;
import com.agenttaskmanager.app.model.session.CodexSessionEventModels.SessionEventType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

final class InMemorySessionRuntimeEventProjector {

  private final CodexClientPlatformProperties properties;

  InMemorySessionRuntimeEventProjector(CodexClientPlatformProperties properties) {
    this.properties = properties;
  }

  void apply(
      InMemoryCodexSessionState session,
      RuntimeEventPublishRequest request,
      SessionEventType eventType,
      Map<String, Object> attributes
  ) {
    String backendTurnId = InMemorySessionProjectionSupport.blankToNull(request.turnId());
    updateRuntimeState(session, request, attributes);
    refreshRuntimeLease(session, request.occurredAt());
    updateTurnState(session, request, eventType, backendTurnId);
    updateVerifierState(session, request, eventType, backendTurnId, attributes);
    projectOutput(session, request, eventType, backendTurnId, attributes);
    projectToolReceipt(session, request, eventType, backendTurnId, attributes);
    projectArtifacts(session, request, eventType, backendTurnId, attributes);
  }

  private void updateRuntimeState(
      InMemoryCodexSessionState session,
      RuntimeEventPublishRequest request,
      Map<String, Object> attributes
  ) {
    String threadId = InMemorySessionProjectionSupport.blankToNull(request.threadId());
    if (threadId == null) {
      threadId = InMemorySessionProjectionSupport.stringValue(attributes, "threadId", session.runtimeConnection.threadId());
    } else {
      attributes.putIfAbsent("threadId", threadId);
    }
    String runtimeTurnId = InMemorySessionProjectionSupport.stringValue(
        attributes,
        "runtimeTurnId",
        session.runtimeConnection.lastTurnId()
    );
    session.runtimeConnection = new RuntimeConnection(
        session.runtimeConnection.runtimeId(),
        RuntimeConnectionState.CONNECTED,
        session.runtimeConnection.transportKind(),
        session.runtimeConnection.authMode(),
        session.runtimeConnection.preferredModel(),
        session.runtimeConnection.endpointDescription(),
        session.runtimeConnection.schemaVersion(),
        request.occurredAt(),
        threadId,
        runtimeTurnId,
        session.runtimeConnection.lastDisconnectReason()
    );
  }

  private void refreshRuntimeLease(InMemoryCodexSessionState session, OffsetDateTime occurredAt) {
    session.runtimeLease = new RuntimeLease(
        session.sessionId,
        RuntimeLeaseState.ACTIVE_OWNER,
        session.runtimeLease.ownerDeviceId(),
        session.runtimeLease.ownerHostName(),
        session.runtimeLease.handoffAllowed(),
        occurredAt.plusSeconds(properties.getRuntimeLeaseTtlSeconds()),
        true
    );
  }

  private void updateTurnState(
      InMemoryCodexSessionState session,
      RuntimeEventPublishRequest request,
      SessionEventType eventType,
      String backendTurnId
  ) {
    if (backendTurnId == null) {
      return;
    }
    int turnIndex = InMemorySessionProjectionSupport.findTurnIndex(session, backendTurnId);
    if (turnIndex < 0) {
      return;
    }
    TurnSummary existing = session.turns.get(turnIndex);
    if (eventType == SessionEventType.TURN_STARTED) {
      session.turns.set(turnIndex, replaceTurn(existing, "running", request.occurredAt(), existing.awaitingVerifier(), existing.approvedOutputAvailable()));
      return;
    }
    if ("turn/completed".equals(request.rawNotificationName())) {
      session.turns.set(turnIndex, replaceTurn(existing, "completed", request.occurredAt(), existing.awaitingVerifier(), existing.approvedOutputAvailable()));
      return;
    }
    if (eventType == SessionEventType.APPROVED_OUTPUT_RELEASED) {
      session.turns.set(turnIndex, replaceTurn(existing, "completed", request.occurredAt(), false, true));
      return;
    }
    if (eventType == SessionEventType.OUTPUT_BLOCKED) {
      session.turns.set(turnIndex, replaceTurn(existing, "blocked", request.occurredAt(), false, false));
    }
  }

  private void updateVerifierState(
      InMemoryCodexSessionState session,
      RuntimeEventPublishRequest request,
      SessionEventType eventType,
      String backendTurnId,
      Map<String, Object> attributes
  ) {
    if (backendTurnId == null) {
      return;
    }
    int verifierIndex = InMemorySessionProjectionSupport.findVerifierIndex(session, backendTurnId);
    if (verifierIndex < 0) {
      return;
    }
    VerifierResult existing = session.verifierResults.get(verifierIndex);
    if (eventType == SessionEventType.VERIFIER_PASSED || eventType == SessionEventType.APPROVED_OUTPUT_RELEASED) {
      session.verifierResults.set(verifierIndex, replaceVerifier(
          existing,
          "passed",
          false,
          request,
          attributes
      ));
      return;
    }
    if (eventType == SessionEventType.VERIFIER_FAILED || eventType == SessionEventType.OUTPUT_BLOCKED) {
      session.verifierResults.set(verifierIndex, replaceVerifier(
          existing,
          "failed",
          true,
          request,
          attributes
      ));
    }
  }

  private void projectOutput(
      InMemoryCodexSessionState session,
      RuntimeEventPublishRequest request,
      SessionEventType eventType,
      String backendTurnId,
      Map<String, Object> attributes
  ) {
    if (eventType == SessionEventType.OUTPUT_BLOCKED) {
      session.outputReleaseState = OutputReleaseState.BLOCKED;
      session.lifecycleState = SessionLifecycleState.BLOCKED;
      return;
    }
    if (eventType != SessionEventType.CANDIDATE_OUTPUT_PRODUCED
        && eventType != SessionEventType.APPROVED_OUTPUT_RELEASED) {
      return;
    }
    Map<?, ?> item = InMemorySessionProjectionSupport.mapValue(attributes.get("item"));
    boolean approved = eventType == SessionEventType.APPROVED_OUTPUT_RELEASED;
    String outputId = InMemorySessionProjectionSupport.stringValue(item, "id", "output_" + UUID.randomUUID());
    String content = InMemorySessionProjectionSupport.nonBlank(
        InMemorySessionProjectionSupport.stringValue(item, "text", ""),
        InMemorySessionProjectionSupport.extractContentText(item),
        request.summary()
    );
    session.outputs.removeIf(output -> output.outputId().equals(outputId) && output.approved() == approved);
    session.outputs.add(new OutputSnapshot(
        outputId,
        InMemorySessionProjectionSupport.nullToEmpty(backendTurnId),
        approved,
        InMemorySessionProjectionSupport.summarizeOutput(item, content, approved),
        content,
        approved ? OutputReleaseState.APPROVED : OutputReleaseState.CANDIDATE_ONLY,
        request.occurredAt()
    ));
    session.outputReleaseState = approved ? OutputReleaseState.APPROVED : OutputReleaseState.CANDIDATE_ONLY;
    session.lifecycleState = approved ? SessionLifecycleState.COMPLETED : SessionLifecycleState.AWAITING_APPROVAL;
  }

  private void projectToolReceipt(
      InMemoryCodexSessionState session,
      RuntimeEventPublishRequest request,
      SessionEventType eventType,
      String backendTurnId,
      Map<String, Object> attributes
  ) {
    if (eventType != SessionEventType.TOOL_RECEIPT_PUBLISHED) {
      return;
    }
    Map<?, ?> item = InMemorySessionProjectionSupport.mapValue(attributes.get("item"));
    String receiptId = InMemorySessionProjectionSupport.stringValue(item, "id", "receipt_" + UUID.randomUUID());
    session.toolReceipts.removeIf(receipt -> receipt.receiptId().equals(receiptId));
    session.toolReceipts.add(new ToolReceipt(
        receiptId,
        InMemorySessionProjectionSupport.nullToEmpty(backendTurnId),
        InMemorySessionProjectionSupport.nonBlank(
            InMemorySessionProjectionSupport.stringValue(item, "toolName", ""),
            InMemorySessionProjectionSupport.stringValue(item, "command", ""),
            InMemorySessionProjectionSupport.stringValue(item, "type", ""),
            "tool"
        ),
        InMemorySessionProjectionSupport.stringValue(item, "type", "tool"),
        InMemorySessionProjectionSupport.stringValue(item, "status", "completed"),
        InMemorySessionProjectionSupport.nonBlank(request.summary(), "Tool receipt recorded."),
        request.occurredAt()
    ));
  }

  private void projectArtifacts(
      InMemoryCodexSessionState session,
      RuntimeEventPublishRequest request,
      SessionEventType eventType,
      String backendTurnId,
      Map<String, Object> attributes
  ) {
    if (eventType == SessionEventType.PATCH_PUBLISHED) {
      session.patches.add(new PatchArtifact(
          "patch_" + UUID.randomUUID(),
          InMemorySessionProjectionSupport.nullToEmpty(backendTurnId),
          InMemorySessionProjectionSupport.nullToEmpty(session.workspaceBinding.repoPath()),
          InMemorySessionProjectionSupport.stringValue(attributes, "baseRevision", ""),
          InMemorySessionProjectionSupport.stringValue(attributes, "headRevision", ""),
          request.summary(),
          InMemorySessionProjectionSupport.stringValue(attributes, "diffPreview", ""),
          InMemorySessionProjectionSupport.stringValue(attributes, "artifactBodyReference", ""),
          InMemorySessionProjectionSupport.parseChangedFiles(attributes.get("changedFiles")),
          request.occurredAt()
      ));
    }
    if (eventType == SessionEventType.FILE_FOCUS_REQUESTED || eventType == SessionEventType.EXTERNAL_EDITOR_OPEN_REQUESTED) {
      session.fileFocusRequests.add(new FileFocusRequest(
          "focus_" + UUID.randomUUID(),
          InMemorySessionProjectionSupport.nullToEmpty(backendTurnId),
          InMemorySessionProjectionSupport.stringValue(attributes, "path", ""),
          InMemorySessionProjectionSupport.intValue(attributes.get("line")),
          InMemorySessionProjectionSupport.intValue(attributes.get("column")),
          request.summary(),
          InMemorySessionProjectionSupport.stringValue(attributes, "launchHint", ""),
          request.occurredAt()
      ));
    }
  }

  private static TurnSummary replaceTurn(
      TurnSummary existing,
      String status,
      OffsetDateTime lastUpdatedAt,
      boolean awaitingVerifier,
      boolean approvedOutputAvailable
  ) {
    return new TurnSummary(
        existing.turnId(),
        status,
        existing.requestedMode(),
        existing.requestedBy(),
        existing.createdAt(),
        lastUpdatedAt,
        awaitingVerifier,
        approvedOutputAvailable
    );
  }

  private static VerifierResult replaceVerifier(
      VerifierResult existing,
      String status,
      boolean blocking,
      RuntimeEventPublishRequest request,
      Map<String, Object> attributes
  ) {
    return new VerifierResult(
        existing.verifierId(),
        existing.turnId(),
        status,
        blocking,
        InMemorySessionProjectionSupport.nonBlank(request.summary(), existing.summary()),
        InMemorySessionProjectionSupport.stringValue(attributes, "evidenceUri", existing.evidenceUri()),
        request.occurredAt()
    );
  }
}
