package org.tavall.ai.app.desktop;

import org.tavall.ai.app.computeruse.ComputerUseRunnerService;
import org.tavall.ai.app.model.computeruse.ComputerUseRunnerRegistration;
import org.tavall.ai.app.model.computeruse.ComputerUseRunnerSummary;
import org.tavall.ai.app.model.computeruse.ComputerUseSessionRequest;
import org.tavall.ai.app.model.computeruse.ComputerUseSessionSummary;
import org.tavall.ai.app.persistence.postgres.ComputerUseArtifactRepository;
import org.tavall.ai.app.persistence.postgres.ComputerUseRunnerRepository;
import org.tavall.ai.app.persistence.postgres.ComputerUseSessionRepository;
import org.tavall.ai.app.persistence.postgres.DesktopRunnerSelectionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DesktopRemoteRunnerService {

  private final ComputerUseRunnerRepository runnerRepository;
  private final ComputerUseRunnerService computerUseRunnerService;
  private final ComputerUseSessionRepository sessionRepository;
  private final ComputerUseArtifactRepository artifactRepository;
  private final DesktopRunnerSelectionRepository selectionRepository;
  private final DesktopRemoteRunnerMapper mapper;
  private final ObjectMapper objectMapper;

  public DesktopRemoteRunnerService(
      ComputerUseRunnerRepository runnerRepository,
      ComputerUseRunnerService computerUseRunnerService,
      ComputerUseSessionRepository sessionRepository,
      ComputerUseArtifactRepository artifactRepository,
      DesktopRunnerSelectionRepository selectionRepository,
      DesktopRemoteRunnerMapper mapper,
      ObjectMapper objectMapper
  ) {
    this.runnerRepository = runnerRepository;
    this.computerUseRunnerService = computerUseRunnerService;
    this.sessionRepository = sessionRepository;
    this.artifactRepository = artifactRepository;
    this.selectionRepository = selectionRepository;
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  public List<Map<String, Object>> listProfiles() {
    String selectedProfileId = selectionRepository.loadSelectedProfileId().orElse("");
    return runnerRepository.listRunners().stream()
        .map(runner -> mapper.toProfile(runner, selectedProfileId))
        .toList();
  }

  public Map<String, Object> upsertProfile(String profileId, Map<String, Object> profilePayload) {
    Map<String, Object> payload = profilePayload == null ? Map.of() : profilePayload;
    String normalizedProfileId = normalizeProfileId(profileId);
    Optional<ComputerUseRunnerSummary> existing = runnerRepository.findRunner(normalizedProfileId);
    Map<String, Object> existingMetadata = existing.map(ComputerUseRunnerSummary::metadata).orElse(Map.of());

    String displayName = mapper.readString(payload.get("displayName"), existing.map(ComputerUseRunnerSummary::displayName).orElse("Remote Runner"));
    String baseUrl = mapper.normalizeBaseUrl(mapper.readString(payload.get("baseUrl"), existing.map(ComputerUseRunnerSummary::baseUrl).orElse("http://127.0.0.1:54123")));
    String hostName = mapper.readString(payload.get("hostName"), mapper.extractHost(baseUrl));
    String launcherPath = mapper.readString(payload.get("launcherPath"), existing.map(ComputerUseRunnerSummary::launcherPath).orElse(""));
    String clientPath = mapper.readString(payload.get("clientPath"), existing.map(ComputerUseRunnerSummary::clientPath).orElse(""));

    Map<String, Object> metadata = new LinkedHashMap<>(existingMetadata);
    metadata.put("transportMode", mapper.readString(payload.get("transportMode"), mapper.readString(existingMetadata.get("transportMode"), "DIRECT_HTTP")));
    metadata.put("sshHost", mapper.readString(payload.get("sshHost"), mapper.readString(existingMetadata.get("sshHost"), "")));
    metadata.put("sshPort", mapper.readInteger(payload.get("sshPort"), mapper.readInteger(existingMetadata.get("sshPort"), 22)));
    metadata.put("sshUser", mapper.readString(payload.get("sshUser"), mapper.readString(existingMetadata.get("sshUser"), "ubuntu")));
    metadata.put(
        "runnerAuthTokenReference",
        mapper.readString(payload.get("runnerAuthTokenReference"), mapper.readString(existingMetadata.get("runnerAuthTokenReference"), ""))
    );
    metadata.put(
        "defaultScenarioId",
        mapper.readString(payload.get("defaultScenarioId"), mapper.readString(existingMetadata.get("defaultScenarioId"), "hytale/launch-and-join-smoke"))
    );
    metadata.put("terminalCommand", mapper.readString(payload.get("terminalCommand"), mapper.readString(existingMetadata.get("terminalCommand"), "")));

    ComputerUseRunnerRegistration registration = new ComputerUseRunnerRegistration(
        normalizedProfileId,
        displayName,
        hostName,
        baseUrl,
        launcherPath,
        clientPath,
        existing.map(ComputerUseRunnerSummary::supportedCaptureModes).orElse(List.of("window", "display")),
        existing.map(ComputerUseRunnerSummary::capabilities).orElse(Map.of()),
        metadata
    );

    ComputerUseRunnerSummary runner = runnerRepository.upsertRunner(registration);
    boolean selected = mapper.readBoolean(payload.get("selected"), false);
    if (selected || selectionRepository.loadSelectedProfileId().isEmpty()) {
      selectionRepository.saveSelectedProfileId(normalizedProfileId);
    }

    String selectedProfileId = selectionRepository.loadSelectedProfileId().orElse("");
    return mapper.toProfile(runner, selectedProfileId);
  }

  public void deleteProfile(String profileId) {
    String normalizedProfileId = normalizeProfileId(profileId);
    runnerRepository.deleteRunner(normalizedProfileId);
    selectionRepository.clearSelectedProfile(normalizedProfileId);
    if (selectionRepository.loadSelectedProfileId().isEmpty()) {
      runnerRepository.listRunners().stream()
          .findFirst()
          .ifPresent(runner -> selectionRepository.saveSelectedProfileId(runner.runnerId()));
    }
  }

  public void selectProfile(String profileId) {
    String normalizedProfileId = normalizeProfileId(profileId);
    runnerRepository.getRunner(normalizedProfileId);
    selectionRepository.saveSelectedProfileId(normalizedProfileId);
  }

  public Map<String, Object> testProfile(String profileId, Map<String, Object> payload) {
    Map<String, Object> profile = payload == null ? Map.of() : payload;
    String normalizedProfileId = normalizeProfileId(mapper.readString(profile.get("profileId"), profileId));
    Optional<ComputerUseRunnerSummary> existing = runnerRepository.findRunner(normalizedProfileId);

    String baseUrl = mapper.normalizeBaseUrl(mapper.readString(profile.get("baseUrl"), existing.map(ComputerUseRunnerSummary::baseUrl).orElse("")));
    if (baseUrl.isBlank()) {
      throw new IllegalArgumentException("Runner baseUrl is required for connectivity tests.");
    }
    String token = mapper.readString(profile.get("runnerAuthToken"), "");

    String healthStatus = "unreachable";
    String capabilitiesSummary = "capabilities unavailable";
    String commandPath = "/api/automation/command";
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
      HttpRequest.Builder healthRequest = HttpRequest.newBuilder()
          .uri(java.net.URI.create(baseUrl + "/api/automation/health"))
          .timeout(Duration.ofSeconds(10))
          .GET();
      if (!token.isBlank()) {
        healthRequest.header("Authorization", "Bearer " + token);
      }
      HttpResponse<String> healthResponse = client.send(healthRequest.build(), HttpResponse.BodyHandlers.ofString());
      healthStatus = healthResponse.statusCode() < 400 ? "ok" : "http-" + healthResponse.statusCode();

      HttpRequest.Builder capabilitiesRequest = HttpRequest.newBuilder()
          .uri(java.net.URI.create(baseUrl + "/api/automation/capabilities"))
          .timeout(Duration.ofSeconds(10))
          .GET();
      if (!token.isBlank()) {
        capabilitiesRequest.header("Authorization", "Bearer " + token);
      }
      HttpResponse<String> capabilitiesResponse = client.send(
          capabilitiesRequest.build(),
          HttpResponse.BodyHandlers.ofString()
      );
      if (capabilitiesResponse.statusCode() < 400) {
        capabilitiesSummary = "available";
        Map<String, Object> capabilityBody = objectMapper.readValue(capabilitiesResponse.body(), new TypeReference<>() {
        });
        commandPath = mapper.readCommandPath(capabilityBody, commandPath);
      } else {
        capabilitiesSummary = "http-" + capabilitiesResponse.statusCode();
      }
    } catch (Exception exception) {
      Map<String, Object> failed = new LinkedHashMap<>();
      failed.put("success", false);
      failed.put("message", exception.getMessage());
      failed.put("healthStatus", healthStatus);
      failed.put("capabilitiesSummary", capabilitiesSummary);
      failed.put("effectiveCommandPath", commandPath);
      return failed;
    }

    Map<String, Object> passed = new LinkedHashMap<>();
    passed.put("success", "ok".equalsIgnoreCase(healthStatus));
    passed.put(
        "message",
        "ok".equalsIgnoreCase(healthStatus) ? "Runner profile test succeeded." : "Runner profile test failed."
    );
    passed.put("healthStatus", healthStatus);
    passed.put("capabilitiesSummary", capabilitiesSummary);
    passed.put("effectiveCommandPath", commandPath);
    return passed;
  }

  public Map<String, Object> startScenarioRun(Map<String, Object> payload) {
    Map<String, Object> request = payload == null ? Map.of() : payload;
    String profileId = resolveProfileId(request);
    ComputerUseRunnerSummary runner = runnerRepository.getRunner(profileId);
    String defaultScenarioId = mapper.readString(runner.metadata().get("defaultScenarioId"), "hytale/launch-and-join-smoke");
    ComputerUseSessionRequest startRequest = new ComputerUseSessionRequest(
        runner.runnerId(),
        mapper.readString(request.get("taskId"), ""),
        mapper.readString(request.get("workerTaskId"), ""),
        mapper.readString(request.get("scenarioId"), defaultScenarioId),
        mapper.readString(request.get("serverTarget"), ""),
        mapper.readString(request.get("chartId"), ""),
        List.of(),
        List.of(),
        Map.of(),
        Map.of("source", "desktop-remote")
    );
    ComputerUseSessionSummary session = computerUseRunnerService.startSession(startRequest);
    return mapper.toScenario(session);
  }

  public Map<String, Object> scenarioStatus(String sessionId) {
    return mapper.toScenario(sessionRepository.getSession(sessionId));
  }

  public List<Map<String, Object>> scenarioArtifacts(String sessionId) {
    return artifactRepository.listArtifacts(sessionId).stream()
        .map(artifact -> {
          Map<String, Object> result = new LinkedHashMap<>();
          result.put("artifactId", artifact.artifactId());
          result.put("sessionId", artifact.sessionId());
          result.put("artifactKind", artifact.artifactKind());
          result.put("storageKey", artifact.storageKey());
          result.put("summary", artifact.summary());
          result.put("metadata", artifact.metadata());
          result.put("createdAt", artifact.createdAt() == null ? null : artifact.createdAt().toString());
          return result;
        })
        .toList();
  }

  private String resolveProfileId(Map<String, Object> request) {
    String requested = normalizeProfileId(
        mapper.readString(request.get("profileId"), mapper.readString(request.get("runnerId"), ""))
    );
    if (!requested.isBlank()) {
      return requested;
    }
    Optional<String> selected = selectionRepository.loadSelectedProfileId();
    if (selected.isPresent()) {
      return selected.get();
    }
    return runnerRepository.listRunners().stream()
        .findFirst()
        .map(ComputerUseRunnerSummary::runnerId)
        .orElseThrow(() -> new IllegalArgumentException("No remote runner profiles are available."));
  }

  private String normalizeProfileId(String profileId) {
    return profileId == null ? "" : profileId.strip();
  }
}

