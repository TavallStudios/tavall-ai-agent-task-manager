package com.agenttaskmanager.app.model.session;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;

public final class CodexSessionApiModels {

  private CodexSessionApiModels() {
  }

  public enum ClientSurface {
    DESKTOP,
    VSCODE,
    INTELLIJ,
    WEB
  }

  public enum WorkspaceScope {
    REPOSITORY,
    BOUNDED_ROOT,
    UTILITY
  }

  public enum SessionLifecycleState {
    CREATED,
    ATTACHED,
    ACTIVE,
    AWAITING_APPROVAL,
    AWAITING_VERIFIER,
    BLOCKED,
    PAUSED,
    COMPLETED,
    FAILED
  }

  public enum RuntimeConnectionState {
    DISCONNECTED,
    STARTING,
    CONNECTED,
    RECONNECTING,
    DEGRADED
  }

  public enum RuntimeLeaseState {
    UNASSIGNED,
    ACTIVE_OWNER,
    OBSERVER,
    HANDOFF_PENDING,
    RECOVERY_REQUIRED
  }

  public enum OutputReleaseState {
    NONE,
    CANDIDATE_ONLY,
    APPROVED,
    BLOCKED
  }

  public record CreateSessionRequest(
      @NotBlank String title,
      String projectKey,
      String repoPath,
      @NotBlank String workspaceRoot,
      String profileKey,
      ClientSurface clientSurface,
      WorkspaceScope workspaceScope,
      boolean utilitySession,
      boolean createRuntime,
      String initialPrompt
  ) {
  }

  public record AttachSessionRequest(
      @NotBlank String deviceId,
      @NotBlank String deviceName,
      ClientSurface clientSurface,
      String hostName,
      boolean observeOnly
  ) {
  }

  public record ResumeSessionRequest(
      @NotBlank String deviceId,
      String hostName,
      boolean requestOwnership,
      boolean allowRuntimeHandoff
  ) {
  }

  public record SubmitTurnRequest(
      @NotBlank String promptText,
      @NotBlank String requestedMode,
      List<String> requiredReceiptKinds,
      boolean allowFileEdits
  ) {
  }

  public record ListSessionsResponse(List<SessionSummary> items) {
  }

  public record SessionDetail(
      SessionSummary summary,
      WorkspaceBinding workspaceBinding,
      RuntimeConnection runtimeConnection,
      RuntimeLease runtimeLease,
      List<DevicePresence> devices,
      List<TurnSummary> turns,
      List<ToolReceipt> toolReceipts,
      List<VerifierResult> verifierResults,
      List<OutputSnapshot> outputs,
      List<PatchArtifact> patches,
      List<FileFocusRequest> fileFocusRequests,
      List<MemoryContextReference> memoryReferences,
      List<CodexSessionEventModels.SessionEventEnvelope> recentEvents
  ) {
  }

  public record SessionSummary(
      String sessionId,
      String title,
      String projectKey,
      String repoPath,
      String workspaceRoot,
      ClientSurface clientSurface,
      SessionLifecycleState lifecycleState,
      RuntimeConnectionState runtimeConnectionState,
      OutputReleaseState outputReleaseState,
      boolean remotelyResumable,
      OffsetDateTime createdAt,
      OffsetDateTime lastEventAt,
      String activeTurnId,
      String runtimeId
  ) {
  }

  public record WorkspaceBinding(
      String sessionId,
      String projectKey,
      String repoPath,
      String workspaceRoot,
      WorkspaceScope workspaceScope,
      String workingDirectory,
      String profileKey,
      List<ProjectConfigLayer> configLayers,
      List<ResolvedMcpServer> mcpServers,
      String approvalPolicy,
      String sandboxMode,
      boolean utilitySession
  ) {
  }

  public record ProjectConfigLayer(
      String layerName,
      String sourcePath,
      boolean existsOnDisk,
      boolean active,
      boolean trusted
  ) {
  }

  public record ResolvedMcpServer(
      String name,
      String source,
      String transportKind,
      boolean required,
      String value
  ) {
  }

  public record RuntimeConnection(
      String runtimeId,
      RuntimeConnectionState connectionState,
      String transportKind,
      String authMode,
      String preferredModel,
      String endpointDescription,
      String schemaVersion,
      OffsetDateTime lastHeartbeatAt,
      String threadId,
      String lastTurnId,
      String lastDisconnectReason
  ) {
  }

  public record RuntimeLease(
      String sessionId,
      RuntimeLeaseState leaseState,
      String ownerDeviceId,
      String ownerHostName,
      boolean handoffAllowed,
      OffsetDateTime leaseExpiresAt,
      boolean remotelyResumable
  ) {
  }

  public record DevicePresence(
      String deviceId,
      String deviceName,
      ClientSurface clientSurface,
      String hostName,
      String presenceState,
      boolean runtimeOwner,
      OffsetDateTime lastSeenAt
  ) {
  }

  public record TurnSummary(
      String turnId,
      String status,
      String requestedMode,
      String requestedBy,
      OffsetDateTime createdAt,
      OffsetDateTime lastUpdatedAt,
      boolean awaitingVerifier,
      boolean approvedOutputAvailable
  ) {
  }

  public record ToolReceipt(
      String receiptId,
      String turnId,
      String toolName,
      String receiptKind,
      String status,
      String summary,
      OffsetDateTime recordedAt
  ) {
  }

  public record VerifierResult(
      String verifierId,
      String turnId,
      String status,
      boolean blocking,
      String summary,
      String evidenceUri,
      OffsetDateTime recordedAt
  ) {
  }

  public record OutputSnapshot(
      String outputId,
      String turnId,
      boolean approved,
      String summary,
      String content,
      OutputReleaseState releaseState,
      OffsetDateTime recordedAt
  ) {
  }

  public record PatchArtifact(
      String patchId,
      String turnId,
      String repoPath,
      String baseRevision,
      String headRevision,
      String summary,
      String diffPreview,
      String artifactBodyReference,
      List<PatchFileChange> changedFiles,
      OffsetDateTime recordedAt
  ) {
  }

  public record PatchFileChange(
      String path,
      String changeType,
      int addedLines,
      int removedLines
  ) {
  }

  public record FileFocusRequest(
      String requestId,
      String turnId,
      String path,
      Integer line,
      Integer column,
      String reason,
      String launchHint,
      OffsetDateTime createdAt
  ) {
  }

  public record MemoryContextReference(
      String referenceId,
      String turnId,
      String memoryKind,
      String sourceType,
      String summary,
      String bodyPreview,
      OffsetDateTime recordedAt
  ) {
  }
}
