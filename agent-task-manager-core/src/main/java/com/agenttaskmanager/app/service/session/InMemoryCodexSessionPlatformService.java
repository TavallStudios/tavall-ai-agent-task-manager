package com.agenttaskmanager.app.service.session;

import static com.agenttaskmanager.app.service.session.InMemorySessionServiceSupport.afterEventIdIndex;
import static com.agenttaskmanager.app.service.session.InMemorySessionServiceSupport.blankToNull;
import static com.agenttaskmanager.app.service.session.InMemorySessionServiceSupport.parseConnectionState;
import static com.agenttaskmanager.app.service.session.InMemorySessionServiceSupport.requireSession;

import com.agenttaskmanager.app.config.CodexClientPlatformProperties;
import com.agenttaskmanager.app.model.session.CodexRuntimeModels.RuntimeConnectedRequest;
import com.agenttaskmanager.app.model.session.CodexRuntimeModels.RuntimeDisconnectedRequest;
import com.agenttaskmanager.app.model.session.CodexRuntimeModels.RuntimeEventPublishRequest;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.ClientSurface;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.ResumeSessionRequest;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeConnection;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeConnectionState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeLease;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeLeaseState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SessionDetail;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SessionLifecycleState;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SessionSummary;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SubmitTurnRequest;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.TurnSummary;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.VerifierResult;
import com.agenttaskmanager.app.model.session.CodexSessionEventModels.SessionEventEnvelope;
import com.agenttaskmanager.app.model.session.CodexSessionEventModels.SessionEventType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.codex-client-platform", name = "enabled", havingValue = "true")
public class InMemoryCodexSessionPlatformService implements CodexSessionPlatformService {

  private static final String EVENT_SCHEMA_VERSION = "atm.codex-session.v1";

  private final CodexClientPlatformProperties properties;
  private final InMemorySessionEventBroker eventBroker;
  private final InMemorySessionRuntimeEventProjector runtimeEventProjector;
  private final Map<String, InMemoryCodexSessionState> sessions = new ConcurrentHashMap<>();

  public InMemoryCodexSessionPlatformService(
      CodexClientPlatformProperties properties,
      InMemorySessionEventBroker eventBroker
  ) {
    this.properties = properties;
    this.eventBroker = eventBroker;
    this.runtimeEventProjector = new InMemorySessionRuntimeEventProjector(properties);
  }

  @Override
  public SessionSummary createSession(
      com.agenttaskmanager.app.model.session.CodexSessionApiModels.CreateSessionRequest request,
      String userId,
      String deviceId,
      String hostName
  ) {
    InMemoryCodexSessionState session = InMemoryCodexSessionState.create(
        request,
        deviceId,
        hostName,
        properties,
        OffsetDateTime.now()
    );
    sessions.put(session.sessionId, session);
    publish(session, null, SessionEventType.SESSION_CREATED, "backend", OffsetDateTime.now(), Map.of(
        "title", session.title,
        "workspaceRoot", session.workspaceBinding.workspaceRoot()
    ), "Created repo-scoped Codex session.");
    publish(session, null, SessionEventType.WORKSPACE_BOUND, "backend", OffsetDateTime.now(), Map.of(
        "workspaceRoot", session.workspaceBinding.workspaceRoot(),
        "repoPath", nullToEmpty(session.workspaceBinding.repoPath()),
        "profileKey", session.workspaceBinding.profileKey()
    ), "Bound session to explicit workspace scope.");
    publish(session, null, SessionEventType.SESSION_ATTACHED, "backend", OffsetDateTime.now(), Map.of(
        "deviceId", deviceId,
        "clientSurface", session.summary().clientSurface().name()
    ), "Attached originating client device.");
    if (request.createRuntime()) {
      publish(session, null, SessionEventType.RUNTIME_DISCONNECTED, "backend", OffsetDateTime.now(), Map.of(
          "requested", true,
          "runtimeId", session.runtimeConnection.runtimeId()
      ), "Runtime requested. Waiting for the desktop app-server owner to connect.");
    }
    return session.summary();
  }

  @Override
  public List<SessionSummary> listSessions(int limit, String userId) {
    return sessions.values().stream()
        .map(InMemoryCodexSessionState::summary)
        .sorted(Comparator.comparing(SessionSummary::lastEventAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
        .limit(limit)
        .toList();
  }

  @Override
  public SessionDetail getSession(String sessionId, String userId) {
    return requireSession(sessions, sessionId).snapshot();
  }

  @Override
  public SessionDetail attachSession(
      String sessionId,
      com.agenttaskmanager.app.model.session.CodexSessionApiModels.AttachSessionRequest request,
      String userId
  ) {
    InMemoryCodexSessionState session = requireSession(sessions, sessionId);
    ClientSurface clientSurface = request.clientSurface() == null
        ? ClientSurface.DESKTOP
        : request.clientSurface();
    session.upsertDevice(request.deviceId(), request.deviceName(), clientSurface, request.hostName(), !request.observeOnly());
    session.lifecycleState = SessionLifecycleState.ATTACHED;
    session.runtimeLease = new RuntimeLease(
        session.sessionId,
        request.observeOnly() ? RuntimeLeaseState.OBSERVER : RuntimeLeaseState.ACTIVE_OWNER,
        request.observeOnly() ? session.runtimeLease.ownerDeviceId() : request.deviceId(),
        request.observeOnly() ? session.runtimeLease.ownerHostName() : nullToEmpty(request.hostName()),
        true,
        OffsetDateTime.now().plusSeconds(properties.getRuntimeLeaseTtlSeconds()),
        true
    );
    publish(session, null, SessionEventType.SESSION_ATTACHED, "backend", OffsetDateTime.now(), Map.of(
        "deviceId", request.deviceId(),
        "clientSurface", clientSurface.name(),
        "observeOnly", request.observeOnly()
    ), "Attached companion surface to session.");
    return session.snapshot();
  }

  @Override
  public SessionDetail resumeSession(String sessionId, ResumeSessionRequest request, String userId) {
    InMemoryCodexSessionState session = requireSession(sessions, sessionId);
    session.lifecycleState = SessionLifecycleState.ATTACHED;
    session.runtimeLease = new RuntimeLease(
        session.sessionId,
        request.requestOwnership() ? RuntimeLeaseState.HANDOFF_PENDING : RuntimeLeaseState.OBSERVER,
        request.requestOwnership() ? request.deviceId() : session.runtimeLease.ownerDeviceId(),
        nullToEmpty(request.hostName()),
        request.allowRuntimeHandoff(),
        OffsetDateTime.now().plusSeconds(properties.getRuntimeLeaseTtlSeconds()),
        true
    );
    publish(session, null, SessionEventType.SESSION_RESUMED, "backend", OffsetDateTime.now(), Map.of(
        "deviceId", request.deviceId(),
        "requestOwnership", request.requestOwnership(),
        "allowRuntimeHandoff", request.allowRuntimeHandoff()
    ), "Resumed session from backend state.");
    return session.snapshot();
  }

  @Override
  public SessionDetail submitTurn(String sessionId, SubmitTurnRequest request, String userId) {
    InMemoryCodexSessionState session = requireSession(sessions, sessionId);
    OffsetDateTime now = OffsetDateTime.now();
    String turnId = "turn_" + UUID.randomUUID();
    session.turns.add(new TurnSummary(turnId, "queued", request.requestedMode(), userId, now, now, true, false));
    session.activeTurnId = turnId;
    session.lifecycleState = SessionLifecycleState.ACTIVE;
    session.verifierResults.add(new VerifierResult(
        "ver_" + UUID.randomUUID(),
        turnId,
        "pending",
        true,
        "Verifier scaffold created. Backend output gate remains closed until approval exists.",
        "",
        now
    ));
    publish(session, turnId, SessionEventType.TURN_STARTED, "backend", now, Map.of(
        "requestedMode", request.requestedMode(),
        "allowFileEdits", request.allowFileEdits()
    ), "Queued turn in backend session timeline.");
    publish(session, turnId, SessionEventType.VERIFIER_STARTED, "backend", now, Map.of(
        "requiredReceiptKinds", request.requiredReceiptKinds() == null ? List.of() : request.requiredReceiptKinds()
    ), "Verifier chain opened before any output release.");
    if (session.runtimeConnection.connectionState() != RuntimeConnectionState.CONNECTED) {
      publish(session, turnId, SessionEventType.RUNTIME_DISCONNECTED, "backend", now, Map.of(
          "runtimeId", session.runtimeConnection.runtimeId(),
          "connectionState", session.runtimeConnection.connectionState().name()
      ), "Turn is queued, but no Codex app-server runtime is currently attached.");
    }
    return session.snapshot();
  }

  @Override
  public SessionDetail markRuntimeConnected(String sessionId, RuntimeConnectedRequest request, String userId) {
    InMemoryCodexSessionState session = requireSession(sessions, sessionId);
    session.runtimeConnection = new RuntimeConnection(
        request.runtimeId(),
        parseConnectionState(request.connectionState()),
        request.transportKind(),
        request.authMode(),
        request.preferredModel(),
        request.endpointDescription(),
        request.schemaVersion(),
        request.lastHeartbeatAt(),
        session.runtimeConnection.threadId(),
        session.runtimeConnection.lastTurnId(),
        session.runtimeConnection.lastDisconnectReason()
    );
    session.runtimeLease = new RuntimeLease(
        session.sessionId,
        RuntimeLeaseState.ACTIVE_OWNER,
        session.runtimeLease.ownerDeviceId(),
        session.runtimeLease.ownerHostName(),
        session.runtimeLease.handoffAllowed(),
        OffsetDateTime.now().plusSeconds(properties.getRuntimeLeaseTtlSeconds()),
        true
    );
    session.lifecycleState = SessionLifecycleState.ACTIVE;
    publish(session, session.activeTurnId, SessionEventType.RUNTIME_RECONNECTED, "desktop-runtime", request.lastHeartbeatAt(), Map.of(
        "runtimeId", request.runtimeId(),
        "transportKind", request.transportKind(),
        "endpointDescription", request.endpointDescription()
    ), "Desktop runtime connected to the backend session.");
    return session.snapshot();
  }

  @Override
  public SessionDetail markRuntimeDisconnected(String sessionId, RuntimeDisconnectedRequest request, String userId) {
    InMemoryCodexSessionState session = requireSession(sessions, sessionId);
    session.runtimeConnection = new RuntimeConnection(
        request.runtimeId(),
        parseConnectionState(request.connectionState()),
        session.runtimeConnection.transportKind(),
        session.runtimeConnection.authMode(),
        session.runtimeConnection.preferredModel(),
        session.runtimeConnection.endpointDescription(),
        session.runtimeConnection.schemaVersion(),
        session.runtimeConnection.lastHeartbeatAt(),
        session.runtimeConnection.threadId(),
        session.runtimeConnection.lastTurnId(),
        nullToEmpty(request.disconnectReason())
    );
    session.runtimeLease = new RuntimeLease(
        session.sessionId,
        request.recoverable() ? RuntimeLeaseState.RECOVERY_REQUIRED : RuntimeLeaseState.UNASSIGNED,
        session.runtimeLease.ownerDeviceId(),
        session.runtimeLease.ownerHostName(),
        session.runtimeLease.handoffAllowed(),
        OffsetDateTime.now().plusSeconds(properties.getRuntimeLeaseTtlSeconds()),
        true
    );
    session.lifecycleState = request.recoverable() ? SessionLifecycleState.PAUSED : SessionLifecycleState.FAILED;
    publish(session, session.activeTurnId, SessionEventType.RUNTIME_DISCONNECTED, "desktop-runtime", request.observedAt(), Map.of(
        "runtimeId", request.runtimeId(),
        "disconnectReason", nullToEmpty(request.disconnectReason()),
        "recoverable", request.recoverable()
    ), "Desktop runtime disconnected from the backend session.");
    return session.snapshot();
  }

  @Override
  public SessionDetail publishRuntimeEvent(String sessionId, RuntimeEventPublishRequest request, String userId) {
    InMemoryCodexSessionState session = requireSession(sessions, sessionId);
    SessionEventType eventType = SessionEventType.fromWireName(request.eventType());
    Map<String, Object> attributes = new LinkedHashMap<>(request.attributes() == null ? Map.of() : request.attributes());
    runtimeEventProjector.apply(session, request, eventType, attributes);
    publish(
        session,
        blankToNull(request.turnId()),
        eventType,
        "desktop-runtime",
        request.occurredAt(),
        attributes,
        request.summary()
    );
    return session.snapshot();
  }

  @Override
  public List<SessionEventEnvelope> listEvents(String sessionId, String afterEventId, int limit, String userId) {
    InMemoryCodexSessionState session = requireSession(sessions, sessionId);
    int startIndex = afterEventIdIndex(session.events, afterEventId);
    int boundedLimit = Math.max(1, Math.min(limit, properties.getMaxEventPageSize()));
    return session.events.subList(startIndex, session.events.size()).stream().limit(boundedLimit).toList();
  }

  @Override
  public SessionEventSubscription subscribeEvents(
      String sessionId,
      String afterEventId,
      int replayLimit,
      Consumer<SessionEventEnvelope> consumer,
      String userId
  ) {
    InMemoryCodexSessionState session = requireSession(sessions, sessionId);
    int startIndex = afterEventIdIndex(session.events, afterEventId);
    if (startIndex == 0 && (afterEventId == null || afterEventId.isBlank())) {
      int boundedReplay = Math.max(1, Math.min(replayLimit, properties.getMaxEventPageSize()));
      startIndex = Math.max(0, session.events.size() - boundedReplay);
    }
    return eventBroker.subscribe(
        sessionId,
        new ArrayList<>(session.events.subList(startIndex, session.events.size())),
        consumer
    );
  }

  private void publish(
      InMemoryCodexSessionState session,
      String turnId,
      SessionEventType eventType,
      String source,
      OffsetDateTime occurredAt,
      Map<String, ?> attributes,
      String summary
  ) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    attributes.forEach(normalized::put);
    InMemorySessionServiceSupport.publish(
        session,
        eventBroker,
        EVENT_SCHEMA_VERSION,
        turnId,
        eventType,
        source,
        occurredAt,
        normalized,
        summary
    );
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
