package com.agenttaskmanager.app.model.session;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class CodexRuntimeModels {

  private CodexRuntimeModels() {
  }

  public enum TransportKind {
    STDIO,
    WEBSOCKET
  }

  public enum AuthMode {
    NONE,
    API_KEY,
    CHATGPT,
    CHATGPT_AUTH_TOKENS
  }

  public record AppServerInitializeRequest(
      String clientName,
      String clientTitle,
      String clientVersion,
      boolean experimentalApi,
      List<String> optOutNotificationMethods
  ) {
  }

  public record AppServerLaunchSpec(
      String runtimeId,
      String sessionId,
      String workingDirectory,
      String profileKey,
      String preferredModel,
      TransportKind transportKind,
      String command,
      List<String> arguments,
      Map<String, String> environment,
      String userLevelConfigPath,
      String projectOverrideConfigPath,
      List<String> requiredMcpServers
  ) {
  }

  public record AppServerAccountState(
      AuthMode authMode,
      boolean requiresOpenAiAuth,
      boolean chatGptRateLimitsVisible,
      String accountLabel,
      OffsetDateTime refreshedAt
  ) {
  }

  public record JsonRpcConnectionState(
      String runtimeId,
      TransportKind transportKind,
      String endpointDescription,
      boolean initialized,
      String lastRequestId,
      String lastNotificationName,
      OffsetDateTime lastHeartbeatAt,
      int consecutiveFailures
  ) {
  }

  public record RuntimeHealthSnapshot(
      String runtimeId,
      String sessionId,
      String status,
      String schemaVersion,
      String healthSummary,
      OffsetDateTime observedAt
  ) {
  }

  public record RuntimeOwnershipSnapshot(
      String sessionId,
      String ownerDeviceId,
      String ownerHostName,
      boolean remotelyResumable,
      boolean handoffAllowed,
      OffsetDateTime leaseExpiresAt
  ) {
  }

  public record RuntimeConnectedRequest(
      String runtimeId,
      String connectionState,
      String transportKind,
      String authMode,
      String preferredModel,
      String endpointDescription,
      String schemaVersion,
      OffsetDateTime lastHeartbeatAt
  ) {
  }

  public record RuntimeDisconnectedRequest(
      String runtimeId,
      String connectionState,
      String disconnectReason,
      boolean recoverable,
      OffsetDateTime observedAt
  ) {
  }

  public record RuntimeEventPublishRequest(
      String runtimeId,
      long sequenceNumber,
      String eventType,
      String threadId,
      String turnId,
      OffsetDateTime occurredAt,
      String summary,
      Map<String, Object> attributes,
      String rawNotificationName
  ) {
  }
}
