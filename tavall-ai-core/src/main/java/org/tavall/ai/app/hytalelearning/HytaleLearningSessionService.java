package org.tavall.ai.app.hytalelearning;

import org.tavall.ai.app.model.BridgeSessionNotFoundException;
import org.tavall.ai.app.model.hytalelearning.HytaleActionTrace;
import org.tavall.ai.app.model.hytalelearning.HytaleActionTraceRequest;
import org.tavall.ai.app.model.hytalelearning.HytaleLearningSession;
import org.tavall.ai.app.model.hytalelearning.HytaleLearningSessionRequest;
import org.tavall.ai.app.model.hytalelearning.HytaleTimelineFrame;
import org.tavall.ai.app.model.hytalelearning.HytaleTimelineFrameRequest;
import org.tavall.ai.app.model.hytalelearning.HytaleVisualAnchor;
import org.tavall.ai.app.model.hytalelearning.HytaleVisualAnchorRequest;
import org.tavall.ai.app.persistence.postgres.BridgeSessionRepository;
import org.tavall.ai.app.persistence.postgres.HytaleActionTraceRepository;
import org.tavall.ai.app.persistence.postgres.HytaleLearningSessionRepository;
import org.tavall.ai.app.persistence.postgres.HytaleTimelineFrameRepository;
import org.tavall.ai.app.persistence.postgres.HytaleVisualAnchorRepository;
import org.tavall.ai.app.persistence.redis.OrchestrationHotStateStore;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class HytaleLearningSessionService {

  private static final Duration HOT_STATE_TTL = Duration.ofMinutes(10);

  private final BridgeSessionRepository bridgeSessionRepository;
  private final HytaleActionTraceRepository actionTraceRepository;
  private final HytaleLearningArtifactService learningArtifactService;
  private final HytaleLearningSessionRepository learningSessionRepository;
  private final HytaleTimelineFrameRepository timelineFrameRepository;
  private final HytaleVisualAnchorRepository visualAnchorRepository;
  private final OrchestrationHotStateStore orchestrationHotStateStore;

  public HytaleLearningSessionService(
      BridgeSessionRepository bridgeSessionRepository,
      HytaleActionTraceRepository actionTraceRepository,
      HytaleLearningArtifactService learningArtifactService,
      HytaleLearningSessionRepository learningSessionRepository,
      HytaleTimelineFrameRepository timelineFrameRepository,
      HytaleVisualAnchorRepository visualAnchorRepository,
      OrchestrationHotStateStore orchestrationHotStateStore
  ) {
    this.bridgeSessionRepository = bridgeSessionRepository;
    this.actionTraceRepository = actionTraceRepository;
    this.learningArtifactService = learningArtifactService;
    this.learningSessionRepository = learningSessionRepository;
    this.timelineFrameRepository = timelineFrameRepository;
    this.visualAnchorRepository = visualAnchorRepository;
    this.orchestrationHotStateStore = orchestrationHotStateStore;
  }

  public HytaleLearningSession createSession(HytaleLearningSessionRequest request) {
    if (request.bridgeSessionId() != null && !request.bridgeSessionId().isBlank()) {
      bridgeSessionRepository.findBridgeSession(request.bridgeSessionId())
          .orElseThrow(() -> new BridgeSessionNotFoundException(request.bridgeSessionId()));
    }
    HytaleLearningSession session = learningSessionRepository.create(
        "hls_" + UUID.randomUUID(),
        request.bridgeSessionId(),
        request.machineId(),
        request.clientProfileId(),
        request.clientInstallPath(),
        request.serverTarget(),
        request.scenarioId(),
        request.metadata()
    );
    recordHotState(session.sessionId(), "session-started", true, Map.of("machineId", session.machineId()));
    return session;
  }

  public HytaleLearningSession getSession(String sessionId) {
    return learningSessionRepository.get(sessionId);
  }

  public List<HytaleLearningSession> listSessions(
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId,
      int limit
  ) {
    return learningSessionRepository.listByScope(machineId, clientProfileId, serverTarget, scenarioId, limit);
  }

  public HytaleLearningSession updateSessionStatus(
      String sessionId,
      String status,
      String latestSummary,
      Map<String, Object> metadata
  ) {
    HytaleLearningSession session = learningSessionRepository.updateStatus(
        sessionId,
        status,
        latestSummary,
        metadata == null ? Map.of() : metadata,
        status != null && status.equalsIgnoreCase("completed")
    );
    recordHotState(session.sessionId(), status, true, Map.of("latestSummary", blank(latestSummary)));
    return session;
  }

  public HytaleActionTrace recordActionTrace(String sessionId, HytaleActionTraceRequest request) {
    getSession(sessionId);
    HytaleActionTrace trace = actionTraceRepository.create(
        "hat_" + UUID.randomUUID(),
        sessionId,
        request.commandRequestId(),
        request.actionKind(),
        request.commandId(),
        request.status(),
        request.summary(),
        request.payload()
    );
    recordHotState(sessionId, request.actionKind(), true, Map.of("traceStatus", blank(request.status())));
    return trace;
  }

  public List<HytaleActionTrace> listActionTraces(String sessionId, int limit) {
    getSession(sessionId);
    return actionTraceRepository.listForSession(sessionId, limit);
  }

  public HytaleTimelineFrame recordTimelineFrame(String sessionId, HytaleTimelineFrameRequest request) {
    HytaleLearningSession session = getSession(sessionId);
    HytaleTimelineFrame frame = learningArtifactService.storeTimelineFrame(session, request);
    recordHotState(sessionId, "timeline-captured", true, Map.of("sourceWindow", blank(request.sourceWindow())));
    return frame;
  }

  public List<HytaleTimelineFrame> listTimelineFrames(String sessionId, int limit) {
    getSession(sessionId);
    return timelineFrameRepository.listForSession(sessionId, limit);
  }

  public HytaleVisualAnchor upsertVisualAnchor(HytaleVisualAnchorRequest request) {
    HytaleVisualAnchor anchor = learningArtifactService.storeVisualAnchor(request);
    recordHotState(
        anchor.anchorId(),
        "anchor-validated",
        true,
        Map.of("anchorKey", blank(anchor.anchorKey()), "sourceWindow", blank(anchor.sourceWindow()))
    );
    return anchor;
  }

  public List<HytaleVisualAnchor> listVisualAnchors(
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId,
      int limit
  ) {
    return visualAnchorRepository.listByScope(machineId, clientProfileId, serverTarget, scenarioId, limit);
  }

  public Map<Object, Object> getHotState(String sessionId) {
    getSession(sessionId);
    return orchestrationHotStateStore.getHytaleSessionState(sessionId);
  }

  private void recordHotState(
      String learningSessionId,
      String automationPhase,
      boolean focusSafe,
      Map<String, String> metadata
  ) {
    Map<String, String> values = new LinkedHashMap<>();
    if (metadata != null) {
      values.putAll(metadata);
    }
    orchestrationHotStateStore.recordHytaleSessionState(
        learningSessionId,
        automationPhase,
        focusSafe,
        values,
        HOT_STATE_TTL
    );
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }
}

