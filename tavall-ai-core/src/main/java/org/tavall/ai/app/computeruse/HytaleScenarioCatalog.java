package org.tavall.ai.app.computeruse;

import org.tavall.ai.app.config.ComputerUseProperties;
import org.tavall.ai.app.model.computeruse.ComputerUseScenarioDefinition;
import org.tavall.ai.app.model.computeruse.ComputerUseScenarioStep;
import org.tavall.ai.app.model.computeruse.ComputerUseSessionRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HytaleScenarioCatalog {

  private final ComputerUseProperties properties;

  public HytaleScenarioCatalog(ComputerUseProperties properties) {
    this.properties = properties;
  }

  public ScenarioDefaults resolve(ComputerUseSessionRequest request) {
    String scenarioId = defaultScenarioId(request.scenarioId());
    String serverTarget = firstNonBlank(request.serverTarget(), properties.getHytale().getServerTarget());
    String chartId = firstNonBlank(request.chartId(), properties.getHytale().getDefaultChartId());
    ComputerUseScenarioDefinition definition = definitionFor(scenarioId, request.artifactPolicy());
    Map<String, Object> metadata = new LinkedHashMap<>(request.metadata() == null ? Map.of() : request.metadata());
    metadata.put("serverTarget", serverTarget);
    metadata.put("chartId", chartId);
    metadata.put("visualAnchors", new LinkedHashMap<>(properties.getHytale().getVisualAnchors()));
    metadata.put("gameplayKeybinds", new LinkedHashMap<>(properties.getHytale().getGameplayKeybinds()));
    metadata.put("scenarioDefinition", definition.toMetadata());
    return new ScenarioDefaults(
        scenarioId,
        serverTarget,
        chartId,
        definition.expectedArtifacts(),
        definition.passFailGates(),
        definition.artifactPolicy(),
        metadata
    );
  }

  private ComputerUseScenarioDefinition definitionFor(String scenarioId, Map<String, Object> requestedArtifactPolicy) {
    return switch (scenarioId) {
      case "hytale/gameplay-assets-visible" -> new ComputerUseScenarioDefinition(
          scenarioId,
          "Launch Hytale, join the target server, start HyRhythm, and prove custom gameplay assets rendered.",
          List.of(
              step("launch-launcher", "Launch launcher", "Launch the Hytale launcher and wait for the launcher-ready anchor.", "launcherReady"),
              step("launch-client", "Launch client", "Launch the client from the runner-local install and reacquire the game window.", "clientReady"),
              step("join-server", "Join server", "Join the configured remote server and verify world join.", "worldJoined"),
              step("start-hyrhythm", "Start HyRhythm", "Open the HyRhythm selection UI and start gameplay.", "songSelect"),
              step("verify-assets", "Verify assets", "Capture the gameplay view and confirm custom assets are present.", "gameplayAssets")
          ),
          List.of("client-ready", "world-joined", "gameplay-assets"),
          List.of("launcherReady", "clientReady", "worldJoined", "gameplayAssets"),
          artifactPolicy(requestedArtifactPolicy)
      );
      case "hytale/chart-start-stable" -> new ComputerUseScenarioDefinition(
          scenarioId,
          "Drive the HyRhythm start flow and confirm gameplay remains stable after chart start.",
          List.of(
              step("open-song-select", "Open song select", "Open the HyRhythm UI and wait for the song-select anchor.", "songSelect"),
              step("select-chart", "Select chart", "Select the configured chart and issue the start command.", "songSelect"),
              step("verify-chart-start", "Verify chart start", "Wait for gameplay assets and chart-start indicators without a disconnect or crash.", "gameplayAssets", "chartStarted")
          ),
          List.of("song-select", "chart-start", "gameplay-assets"),
          List.of("songSelect", "gameplayAssets", "chartStarted"),
          artifactPolicy(requestedArtifactPolicy)
      );
      case "hytale/note-hit-interaction" -> new ComputerUseScenarioDefinition(
          scenarioId,
          "Run a full HyRhythm gameplay pass and prove non-zero note-hit interaction from the external runner.",
          List.of(
              step("verify-assets", "Verify gameplay assets", "Confirm the gameplay UI and custom assets are visible before sending input.", "gameplayAssets"),
              step("drive-input", "Drive note input", "Send the configured gameplay keybind batch against active note lanes.", "nonZeroNoteEvents"),
              step("collect-summary", "Collect summary", "Capture the gameplay result screen and persist note-hit evidence.", "gameplaySummary")
          ),
          List.of("gameplay-assets", "note-hit-result", "gameplay-summary"),
          List.of("gameplayAssets", "nonZeroNoteEvents"),
          artifactPolicy(requestedArtifactPolicy)
      );
      default -> new ComputerUseScenarioDefinition(
          "hytale/launch-and-join-smoke",
          "Launch the Hytale launcher, start the client, and join the configured server from an external runner.",
          List.of(
              step("launch-launcher", "Launch launcher", "Launch the Hytale launcher and wait for readiness.", "launcherReady"),
              step("launch-client", "Launch client", "Launch the client and reacquire the client window.", "clientReady"),
              step("join-server", "Join server", "Join the configured server and verify world join.", "worldJoined")
          ),
          List.of("launcher-ready", "client-ready", "world-joined"),
          List.of("launcherReady", "clientReady", "worldJoined"),
          artifactPolicy(requestedArtifactPolicy)
      );
    };
  }

  private ComputerUseScenarioStep step(String stepId, String title, String objective, String... markers) {
    return new ComputerUseScenarioStep(stepId, title, objective, List.of(markers));
  }

  private Map<String, Object> artifactPolicy(Map<String, Object> requestedArtifactPolicy) {
    Map<String, Object> artifactPolicy = new LinkedHashMap<>(requestedArtifactPolicy == null ? Map.of() : requestedArtifactPolicy);
    artifactPolicy.putIfAbsent("storeFailures", Boolean.TRUE);
    artifactPolicy.putIfAbsent("storeCaptures", Boolean.TRUE);
    return artifactPolicy;
  }

  private String defaultScenarioId(String scenarioId) {
    return scenarioId == null || scenarioId.isBlank() ? "hytale/launch-and-join-smoke" : scenarioId.strip();
  }

  private String firstNonBlank(String first, String fallback) {
    return first != null && !first.isBlank() ? first : fallback;
  }

  public record ScenarioDefaults(
      String scenarioId,
      String serverTarget,
      String chartId,
      List<String> expectedArtifacts,
      List<String> passFailGates,
      Map<String, Object> artifactPolicy,
      Map<String, Object> metadata
  ) {
  }
}

