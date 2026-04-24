package org.tavall.ai.app.model.session;

import java.time.OffsetDateTime;
import java.util.Map;

public final class CodexSessionPersistenceModels {

  private CodexSessionPersistenceModels() {
  }

  public record SessionRecord(
      String sessionId,
      String userId,
      String title,
      String projectKey,
      String repoPath,
      String workspaceRoot,
      String profileKey,
      String lifecycleState,
      String runtimeState,
      String outputReleaseState,
      boolean remotelyResumable,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
  }

  public record TurnRecord(
      String turnId,
      String sessionId,
      String requestedBy,
      String requestedMode,
      String status,
      String promptText,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
  }

  public record EventRecord(
      String eventId,
      String sessionId,
      String turnId,
      String eventType,
      String source,
      String schemaVersion,
      Map<String, Object> attributes,
      String summary,
      OffsetDateTime occurredAt
  ) {
  }

  public record RuntimeLeaseRecord(
      String sessionId,
      String ownerDeviceId,
      String ownerHostName,
      String leaseState,
      boolean handoffAllowed,
      boolean remotelyResumable,
      OffsetDateTime leaseExpiresAt,
      OffsetDateTime updatedAt
  ) {
  }

  public record DevicePresenceRecord(
      String sessionId,
      String deviceId,
      String deviceName,
      String clientSurface,
      String hostName,
      String presenceState,
      boolean runtimeOwner,
      OffsetDateTime lastSeenAt
  ) {
  }

  public record RuntimeConnectionRecord(
      String sessionId,
      String runtimeId,
      String connectionState,
      String transportKind,
      String authMode,
      String preferredModel,
      String endpointDescription,
      String schemaVersion,
      String threadId,
      String lastTurnId,
      String lastDisconnectReason,
      OffsetDateTime lastHeartbeatAt,
      OffsetDateTime updatedAt
  ) {
  }

  public record ToolReceiptRecord(
      String receiptId,
      String sessionId,
      String turnId,
      String toolName,
      String receiptKind,
      String status,
      String summary,
      OffsetDateTime recordedAt
  ) {
  }

  public record VerifierResultRecord(
      String verifierId,
      String sessionId,
      String turnId,
      String status,
      boolean blocking,
      String summary,
      String evidenceUri,
      OffsetDateTime recordedAt
  ) {
  }

  public record OutputRecord(
      String outputId,
      String sessionId,
      String turnId,
      boolean approved,
      String releaseState,
      String summary,
      String content,
      OffsetDateTime recordedAt
  ) {
  }

  public record PatchArtifactRecord(
      String patchId,
      String sessionId,
      String turnId,
      String repoPath,
      String baseRevision,
      String headRevision,
      String summary,
      String diffPreview,
      String artifactBodyReference,
      OffsetDateTime recordedAt
  ) {
  }

  public record PatchFileChangeRecord(
      String patchId,
      String path,
      String changeType,
      int addedLines,
      int removedLines
  ) {
  }

  public record FileFocusRequestRecord(
      String requestId,
      String sessionId,
      String turnId,
      String path,
      Integer line,
      Integer column,
      String reason,
      String launchHint,
      OffsetDateTime createdAt
  ) {
  }

  public record MemoryContextRecord(
      String referenceId,
      String sessionId,
      String turnId,
      String memoryKind,
      String sourceType,
      String summary,
      String bodyPreview,
      OffsetDateTime recordedAt
  ) {
  }
}

