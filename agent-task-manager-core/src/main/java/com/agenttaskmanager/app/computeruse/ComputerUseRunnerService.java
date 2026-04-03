package com.agenttaskmanager.app.computeruse;

import com.agenttaskmanager.app.config.ComputerUseProperties;
import com.agenttaskmanager.app.model.computeruse.ComputerUseCaptureRequest;
import com.agenttaskmanager.app.model.computeruse.ComputerUseCaptureResult;
import com.agenttaskmanager.app.model.computeruse.ComputerUseFramePreview;
import com.agenttaskmanager.app.model.computeruse.ComputerUseInputBatch;
import com.agenttaskmanager.app.model.computeruse.ComputerUseProcessLaunchRequest;
import com.agenttaskmanager.app.model.computeruse.ComputerUseProcessLaunchResult;
import com.agenttaskmanager.app.model.computeruse.ComputerUseRunnerRegistration;
import com.agenttaskmanager.app.model.computeruse.ComputerUseRunnerSummary;
import com.agenttaskmanager.app.model.computeruse.ComputerUseSessionArtifact;
import com.agenttaskmanager.app.model.computeruse.ComputerUseSessionRequest;
import com.agenttaskmanager.app.model.computeruse.ComputerUseSessionSummary;
import com.agenttaskmanager.app.model.computeruse.ComputerUseVisionMatch;
import com.agenttaskmanager.app.model.computeruse.ComputerUseVisionWaitRequest;
import com.agenttaskmanager.app.persistence.postgres.ComputerUseRunnerRepository;
import com.agenttaskmanager.app.persistence.postgres.ComputerUseSessionRepository;
import com.agenttaskmanager.app.persistence.redis.OrchestrationHotStateStore;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ComputerUseRunnerService {

  private final ComputerUseRunnerRepository runnerRepository;
  private final ComputerUseSessionRepository sessionRepository;
  private final ComputerUseRunnerClient runnerClient;
  private final ComputerUseArtifactService artifactService;
  private final HytaleScenarioCatalog scenarioCatalog;
  private final OrchestrationHotStateStore hotStateStore;
  private final ComputerUseProperties properties;

  public ComputerUseRunnerService(
      ComputerUseRunnerRepository runnerRepository,
      ComputerUseSessionRepository sessionRepository,
      ComputerUseRunnerClient runnerClient,
      ComputerUseArtifactService artifactService,
      HytaleScenarioCatalog scenarioCatalog,
      OrchestrationHotStateStore hotStateStore,
      ComputerUseProperties properties
  ) {
    this.runnerRepository = runnerRepository;
    this.sessionRepository = sessionRepository;
    this.runnerClient = runnerClient;
    this.artifactService = artifactService;
    this.scenarioCatalog = scenarioCatalog;
    this.hotStateStore = hotStateStore;
    this.properties = properties;
  }

  public ComputerUseRunnerSummary registerRunner(ComputerUseRunnerRegistration registration) {
    ComputerUseRunnerSummary runner = runnerRepository.upsertRunner(registration);
    hotStateStore.recordComputerUseRunnerLease(
        runner.runnerId(),
        runner.currentLeaseSessionId(),
        Duration.ofSeconds(properties.getRunnerLeaseTtlSeconds())
    );
    return runner;
  }

  public List<ComputerUseRunnerSummary> listRunners() {
    return runnerRepository.listRunners();
  }

  public ComputerUseSessionSummary startSession(ComputerUseSessionRequest request) {
    ComputerUseRunnerSummary runner = runnerRepository.getRunner(request.runnerId());
    runnerClient.ping(runner);
    HytaleScenarioCatalog.ScenarioDefaults defaults = scenarioCatalog.resolve(request);
    ComputerUseSessionSummary session = sessionRepository.createSession(
        new ComputerUseSessionRequest(
            request.runnerId(),
            request.taskId(),
            request.workerTaskId(),
            defaults.scenarioId(),
            defaults.serverTarget(),
            defaults.chartId(),
            defaults.expectedArtifacts(),
            defaults.passFailGates(),
            defaults.artifactPolicy(),
            defaults.metadata()
        ),
        defaults.expectedArtifacts(),
        defaults.passFailGates(),
        defaults.artifactPolicy(),
        defaults.metadata()
    );
    runnerRepository.updateLease(runner.runnerId(), session.sessionId(), "busy");
    hotStateStore.recordComputerUseRunnerLease(
        runner.runnerId(),
        session.sessionId(),
        Duration.ofSeconds(properties.getRunnerLeaseTtlSeconds())
    );
    return session;
  }

  public ComputerUseProcessLaunchResult launchProcess(ComputerUseProcessLaunchRequest request) {
    ComputerUseSessionSummary session = sessionRepository.getSession(request.sessionId());
    ComputerUseRunnerSummary runner = runnerRepository.getRunner(session.runnerId());
    ComputerUseProcessLaunchRequest resolved = resolveLaunchRequest(request, runner);
    Map<String, Object> result = runnerClient.launchProcess(runner, resolved);
    sessionRepository.updateSessionState(session.sessionId(), "RUNNING", "Launched " + resolved.launchTarget(), session.sessionId());
    return new ComputerUseProcessLaunchResult(resolved.fileName(), result);
  }

  public ComputerUseCaptureResult captureWindow(ComputerUseCaptureRequest request) {
    ComputerUseSessionSummary session = sessionRepository.getSession(request.sessionId());
    ComputerUseRunnerSummary runner = runnerRepository.getRunner(session.runnerId());
    ComputerUseCaptureRequest resolved = resolveCaptureRequest(request);
    Map<String, Object> result = runnerClient.captureWindow(runner, resolved);
    ComputerUseSessionArtifact artifact = null;
    if (resolved.storeArtifact()) {
      artifact = artifactService.storeBase64Artifact(
          session,
          resolved.artifactKind(),
          resolved.summary(),
          String.valueOf(result.getOrDefault("base64Png", "")),
          Map.of("captureMode", String.valueOf(result.getOrDefault("captureMode", "")))
      );
    }
    sessionRepository.updateSessionState(session.sessionId(), "RUNNING", resolved.summary(), null);
    return new ComputerUseCaptureResult(
        String.valueOf(result.getOrDefault("captureMode", "")),
        String.valueOf(result.getOrDefault("outputPath", "")),
        artifact
    );
  }

  public Map<String, Object> sendInput(String sessionId, ComputerUseInputBatch batch) {
    ComputerUseSessionSummary session = sessionRepository.getSession(sessionId);
    ComputerUseRunnerSummary runner = runnerRepository.getRunner(session.runnerId());
    Map<String, Object> result = runnerClient.sendInput(runner, batch);
    sessionRepository.updateSessionState(session.sessionId(), "RUNNING", "Input batch sent.", null);
    return result;
  }

  public ComputerUseFramePreview previewFrame(String sessionId, String titleContains, String processName) {
    ComputerUseSessionSummary session = sessionRepository.getSession(sessionId);
    ComputerUseRunnerSummary runner = runnerRepository.getRunner(session.runnerId());
    ComputerUseCaptureRequest request = resolveCaptureRequest(
        new ComputerUseCaptureRequest(
            sessionId,
            titleContains,
            processName,
            false,
            "live-frame",
            "Live frame preview."
        )
    );
    Map<String, Object> result = runnerClient.captureWindow(runner, request);
    sessionRepository.updateSessionState(session.sessionId(), "RUNNING", "Live frame preview refreshed.", null);
    return new ComputerUseFramePreview(
        String.valueOf(result.getOrDefault("captureMode", "")),
        String.valueOf(result.getOrDefault("outputPath", "")),
        String.valueOf(result.getOrDefault("base64Png", "")),
        result.get("bounds") instanceof Map<?, ?> map ? castMap(map) : Map.of()
    );
  }

  public ComputerUseVisionMatch waitForVisionMatch(ComputerUseVisionWaitRequest request) {
    ComputerUseSessionSummary session = sessionRepository.getSession(request.sessionId());
    ComputerUseRunnerSummary runner = runnerRepository.getRunner(session.runnerId());
    ComputerUseVisionWaitRequest resolved = resolveVisionRequest(session, request);
    Instant deadline = Instant.now().plusMillis(Math.max(100, resolved.timeoutMs()));
    Map<String, Object> latest = Map.of();
    do {
      latest = runnerClient.matchTemplate(runner, resolved);
      if (Boolean.TRUE.equals(latest.get("matched")) || asDouble(latest.get("score")) >= resolved.threshold()) {
        return buildVisionMatch(session, resolved, latest, true);
      }
      sleep();
    } while (Instant.now().isBefore(deadline));
    return buildVisionMatch(session, resolved, latest, false);
  }

  public ComputerUseSessionSummary stopSession(String sessionId) {
    ComputerUseSessionSummary session = sessionRepository.getSession(sessionId);
    sessionRepository.updateSessionState(sessionId, "STOPPED", "Session stopped.", null);
    runnerRepository.updateLease(session.runnerId(), null, "online");
    hotStateStore.clearComputerUseRunnerLease(session.runnerId());
    return sessionRepository.getSession(sessionId);
  }

  private ComputerUseProcessLaunchRequest resolveLaunchRequest(
      ComputerUseProcessLaunchRequest request,
      ComputerUseRunnerSummary runner
  ) {
    if (!"launcher".equalsIgnoreCase(request.launchTarget()) && !"client".equalsIgnoreCase(request.launchTarget())) {
      return request;
    }
    boolean launcher = "launcher".equalsIgnoreCase(request.launchTarget());
    return new ComputerUseProcessLaunchRequest(
        request.sessionId(),
        request.launchTarget(),
        firstNonBlank(request.fileName(), launcher ? runner.launcherPath() : runner.clientPath()),
        request.arguments(),
        request.workingDirectory(),
        request.waitForWindowMs() <= 0 ? 15000 : request.waitForWindowMs(),
        firstNonBlank(
            request.windowTitleContains(),
            launcher ? properties.getHytale().getLauncherWindowTitleContains() : properties.getHytale().getClientWindowTitleContains()
        )
    );
  }

  private ComputerUseCaptureRequest resolveCaptureRequest(ComputerUseCaptureRequest request) {
    return new ComputerUseCaptureRequest(
        request.sessionId(),
        firstNonBlank(request.titleContains(), properties.getHytale().getClientWindowTitleContains()),
        firstNonBlank(request.processName(), properties.getHytale().getClientProcessName()),
        request.storeArtifact(),
        firstNonBlank(request.artifactKind(), "window-capture"),
        firstNonBlank(request.summary(), "Captured computer-use window.")
    );
  }

  private ComputerUseVisionWaitRequest resolveVisionRequest(
      ComputerUseSessionSummary session,
      ComputerUseVisionWaitRequest request
  ) {
    @SuppressWarnings("unchecked")
    Map<String, Object> anchors = session.metadata().get("visualAnchors") instanceof Map<?, ?> map
        ? castMap(map)
        : Map.of();
    return new ComputerUseVisionWaitRequest(
        request.sessionId(),
        request.anchorKey(),
        firstNonBlank(request.templatePath(), String.valueOf(anchors.getOrDefault(request.anchorKey(), ""))),
        firstNonBlank(request.titleContains(), properties.getHytale().getClientWindowTitleContains()),
        firstNonBlank(request.processName(), properties.getHytale().getClientProcessName()),
        request.threshold() <= 0 ? 0.95d : request.threshold(),
        request.timeoutMs() <= 0 ? 10000 : request.timeoutMs(),
        request.storeArtifact()
    );
  }

  private ComputerUseVisionMatch buildVisionMatch(
      ComputerUseSessionSummary session,
      ComputerUseVisionWaitRequest request,
      Map<String, Object> result,
      boolean matched
  ) {
    ComputerUseSessionArtifact artifact = null;
    if (request.storeArtifact() && result.containsKey("base64Png")) {
      artifact = artifactService.storeBase64Artifact(
          session,
          "vision-match",
          matched ? "Vision match succeeded." : "Vision match timed out.",
          String.valueOf(result.get("base64Png")),
          Map.of("templatePath", request.templatePath(), "matched", matched)
      );
    }
    sessionRepository.updateSessionState(
        session.sessionId(),
        matched ? "RUNNING" : "FAILED",
        matched ? "Vision anchor matched: " + request.anchorKey() : "Vision anchor timed out: " + request.anchorKey(),
        null
    );
    return new ComputerUseVisionMatch(
        matched,
        asDouble(result.get("score")),
        request.templatePath(),
        result.get("bounds") instanceof Map<?, ?> map ? castMap(map) : Map.of(),
        artifact
    );
  }

  private void sleep() {
    try {
      Thread.sleep(properties.getVisionPollIntervalMs());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while polling for a vision match.", exception);
    }
  }

  private double asDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value == null) {
      return 0d;
    }
    return Double.parseDouble(String.valueOf(value));
  }

  private String firstNonBlank(String first, String fallback) {
    return first != null && !first.isBlank() ? first : fallback;
  }

  private Map<String, Object> castMap(Map<?, ?> source) {
    Map<String, Object> target = new LinkedHashMap<>();
    source.forEach((key, value) -> target.put(String.valueOf(key), value));
    return target;
  }
}
