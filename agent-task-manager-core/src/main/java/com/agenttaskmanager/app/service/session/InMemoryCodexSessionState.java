package com.agenttaskmanager.app.service.session;

import com.agenttaskmanager.app.config.CodexClientPlatformProperties;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.ClientSurface;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.CreateSessionRequest;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.DevicePresence;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.OutputReleaseState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeConnection;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeConnectionState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeLease;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeLeaseState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SessionDetail;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SessionLifecycleState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SessionSummary;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.TurnSummary;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.VerifierResult;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.WorkspaceBinding;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.WorkspaceScope;
import com.agenttaskmanager.app.model.session.CodexSessionEventModels.SessionEventEnvelope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class InMemoryCodexSessionState {

  final String sessionId;
  final String title;
  final OffsetDateTime createdAt;
  final ClientSurface clientSurface;
  final WorkspaceBinding workspaceBinding;
  final List<DevicePresence> devices = new ArrayList<>();
  final List<TurnSummary> turns = new ArrayList<>();
  final List<CodexSessionApiModels.ToolReceipt> toolReceipts = new ArrayList<>();
  final List<VerifierResult> verifierResults = new ArrayList<>();
  final List<CodexSessionApiModels.OutputSnapshot> outputs = new ArrayList<>();
  final List<CodexSessionApiModels.PatchArtifact> patches = new ArrayList<>();
  final List<CodexSessionApiModels.FileFocusRequest> fileFocusRequests = new ArrayList<>();
  final List<CodexSessionApiModels.MemoryContextReference> memoryReferences = new ArrayList<>();
  final List<SessionEventEnvelope> events = new ArrayList<>();
  RuntimeConnection runtimeConnection;
  RuntimeLease runtimeLease;
  SessionLifecycleState lifecycleState;
  OutputReleaseState outputReleaseState;
  OffsetDateTime lastEventAt;
  String activeTurnId;

  private InMemoryCodexSessionState(
      String sessionId,
      String title,
      OffsetDateTime createdAt,
      ClientSurface clientSurface,
      WorkspaceBinding workspaceBinding,
      RuntimeConnection runtimeConnection,
      RuntimeLease runtimeLease
  ) {
    this.sessionId = sessionId;
    this.title = title;
    this.createdAt = createdAt;
    this.clientSurface = clientSurface;
    this.workspaceBinding = workspaceBinding;
    this.runtimeConnection = runtimeConnection;
    this.runtimeLease = runtimeLease;
    this.lifecycleState = SessionLifecycleState.CREATED;
    this.outputReleaseState = OutputReleaseState.NONE;
    this.lastEventAt = createdAt;
  }

  static InMemoryCodexSessionState create(
      CreateSessionRequest request,
      String deviceId,
      String hostName,
      CodexClientPlatformProperties properties,
      OffsetDateTime now
  ) {
    ClientSurface surface = request.clientSurface() == null ? ClientSurface.DESKTOP : request.clientSurface();
    WorkspaceScope scope = request.workspaceScope() == null
        ? (request.utilitySession() ? WorkspaceScope.UTILITY : WorkspaceScope.REPOSITORY)
        : request.workspaceScope();
    String repoPath = scope == WorkspaceScope.REPOSITORY
        ? (request.repoPath() == null || request.repoPath().isBlank() ? request.workspaceRoot() : request.repoPath())
        : nullToEmpty(request.repoPath());
    String sessionId = "csn_" + UUID.randomUUID();
    WorkspaceBinding binding = new WorkspaceBinding(
        sessionId,
        nullToEmpty(request.projectKey()),
        repoPath,
        request.workspaceRoot(),
        scope,
        request.workspaceRoot(),
        request.profileKey() == null || request.profileKey().isBlank() ? properties.getDefaultProfileKey() : request.profileKey(),
        List.of(
            new CodexSessionApiModels.ProjectConfigLayer(
                "user-defaults",
                Path.of(System.getProperty("user.home"), ".codex", "config.toml").toString(),
                Files.exists(Path.of(System.getProperty("user.home"), ".codex", "config.toml")),
                true,
                true
            ),
            new CodexSessionApiModels.ProjectConfigLayer(
                "project-override",
                Path.of(request.workspaceRoot(), ".codex", "config.toml").toString(),
                Files.exists(Path.of(request.workspaceRoot(), ".codex", "config.toml")),
                true,
                false
            )
        ),
        List.of(new CodexSessionApiModels.ResolvedMcpServer(
            "agent-task-manager",
            "desktop-session-default",
            "stdio",
            true,
            "agent-task-manager-mcp"
        )),
        "never",
        scope == WorkspaceScope.UTILITY ? "read-only" : "workspace-write",
        request.utilitySession()
    );
    RuntimeConnection runtimeConnection = new RuntimeConnection(
        "rt_" + UUID.randomUUID(),
        RuntimeConnectionState.DISCONNECTED,
        "json-rpc",
        "unknown",
        properties.getPreferredModel(),
        "supervised codex app-server",
        "codex-app-server/v1",
        now,
        "",
        "",
        ""
    );
    RuntimeLease runtimeLease = new RuntimeLease(
        sessionId,
        RuntimeLeaseState.UNASSIGNED,
        deviceId,
        nullToEmpty(hostName),
        true,
        now.plusSeconds(properties.getRuntimeLeaseTtlSeconds()),
        true
    );
    InMemoryCodexSessionState session = new InMemoryCodexSessionState(
        sessionId,
        request.title(),
        now,
        surface,
        binding,
        runtimeConnection,
        runtimeLease
    );
    session.upsertDevice(deviceId, surface.name() + "-device", surface, hostName, true);
    return session;
  }

  void upsertDevice(
      String deviceId,
      String deviceName,
      ClientSurface scopedSurface,
      String hostName,
      boolean runtimeOwner
  ) {
    devices.removeIf(device -> device.deviceId().equals(deviceId));
    devices.add(new DevicePresence(
        deviceId,
        deviceName,
        scopedSurface == null ? ClientSurface.DESKTOP : scopedSurface,
        nullToEmpty(hostName),
        "connected",
        runtimeOwner,
        OffsetDateTime.now()
    ));
  }

  SessionSummary summary() {
    return new SessionSummary(
        sessionId,
        title,
        workspaceBinding.projectKey(),
        workspaceBinding.repoPath(),
        workspaceBinding.workspaceRoot(),
        clientSurface,
        lifecycleState,
        runtimeConnection.connectionState(),
        outputReleaseState,
        runtimeLease.remotelyResumable(),
        createdAt,
        lastEventAt,
        activeTurnId,
        runtimeConnection.runtimeId()
    );
  }

  SessionDetail snapshot() {
    return new SessionDetail(
        summary(),
        workspaceBinding,
        runtimeConnection,
        runtimeLease,
        List.copyOf(devices),
        List.copyOf(turns),
        List.copyOf(toolReceipts),
        List.copyOf(verifierResults),
        List.copyOf(outputs),
        List.copyOf(patches),
        List.copyOf(fileFocusRequests),
        List.copyOf(memoryReferences),
        List.copyOf(events)
    );
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
