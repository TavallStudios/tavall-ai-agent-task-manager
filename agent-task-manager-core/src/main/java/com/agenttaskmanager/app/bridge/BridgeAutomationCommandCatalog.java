package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.model.bridge.BridgeAutomationCommandDefinition;
import com.agenttaskmanager.app.model.bridge.BridgeAutomationParameterDefinition;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class BridgeAutomationCommandCatalog {

  private static final List<BridgeAutomationCommandDefinition> DEFINITIONS = List.of(
      definition(
          "hytale.launch-launcher",
          "Launch Launcher",
          "Ask the local cooperative provider to start the Hytale launcher without foreground mouse control."
      ),
      definition(
          "hytale.launch-client",
          "Launch Client",
          "Ask the local cooperative provider to launch or attach to the Hytale client process."
      ),
      definition(
          "hytale.join-server",
          "Join Server",
          "Join a configured Hytale server through the local provider.",
          parameter("serverName", "string", false, "Friendly server name or profile to join."),
          parameter("serverAddress", "string", false, "Explicit server address when a saved profile is unavailable.")
      ),
      definition(
          "hytale.close-overlay",
          "Close Overlay",
          "Dismiss a blocking Hytale overlay such as chat without foreground mouse input."
      ),
      definition(
          "hytale.open-creative-tools",
          "Open Creative Tools",
          "Open or recover the in-game Creative Tools panel cooperatively."
      ),
      definition(
          "hytale.open-asset-editor",
          "Open Asset Editor",
          "Open or attach to the Hytale Asset Editor window cooperatively."
      ),
      definition(
          "hytale.asset-editor.navigate",
          "Navigate Asset Editor",
          "Navigate the Hytale Asset Editor tree without raw OS mouse control.",
          parameter("pathSegments", "array", true, "Ordered asset-tree path segments to open.")
      ),
      definition(
          "hytale.capture-timeline",
          "Capture Timeline",
          "Capture timeline evidence for the active Hytale learning session.",
          parameter("learningSessionId", "string", true, "Learning session that should receive the capture."),
          parameter("includeFrame", "boolean", false, "Whether to include a verification frame payload.")
      ),
      definition(
          "hytale.promote-memory",
          "Promote Memory",
          "Request promotion of a successful or resolved Hytale memory item."
      ),
      definition(
          "hytale.list-playbooks",
          "List Playbooks",
          "Request executable Hytale playbooks for the active machine and scenario."
      ),
      definition(
          "hytale.execute-playbook",
          "Execute Playbook",
          "Execute an approved or pinned Hytale playbook cooperatively.",
          parameter("playbookId", "string", true, "Approved or pinned Hytale playbook identifier.")
      ),
      definition(
          "hyrhythm.open-ui",
          "Open HyRhythm UI",
          "Open the HyRhythm custom UI through cooperative game hooks."
      ),
      definition(
          "hyrhythm.select-chart",
          "Select Chart",
          "Select a HyRhythm chart without synthesizing OS mouse clicks.",
          parameter("chartId", "string", true, "Chart identifier such as debug/test-4k.")
      ),
      definition(
          "hyrhythm.start-gameplay",
          "Start Gameplay",
          "Start HyRhythm gameplay through the cooperative automation provider."
      ),
      definition(
          "hyrhythm.press-lane",
          "Press Lane",
          "Emit a high-level lane press event instead of raw keyboard injection.",
          parameter("lane", "integer", true, "Lane index to trigger."),
          parameter("action", "string", false, "Optional action value such as down, up, or tap."),
          parameter("offsetMs", "integer", false, "Optional timing offset relative to provider clock.")
      ),
      definition(
          "hyrhythm.capture-state",
          "Capture State",
          "Capture game state and optional frame evidence through the provider.",
          parameter("includeFrame", "boolean", false, "Whether to include a verification frame in the result.")
      )
  );

  private final Map<String, BridgeAutomationCommandDefinition> definitionsById = DEFINITIONS.stream()
      .collect(Collectors.toUnmodifiableMap(
          BridgeAutomationCommandDefinition::commandId,
          Function.identity()
      ));

  public List<BridgeAutomationCommandDefinition> listDefinitions() {
    return DEFINITIONS;
  }

  public BridgeAutomationCommandDefinition requireDefinition(String commandId) {
    BridgeAutomationCommandDefinition definition = definitionsById.get(commandId);
    if (definition == null) {
      throw new IllegalArgumentException("Unsupported bridge automation command: " + commandId);
    }
    return definition;
  }

  private static BridgeAutomationCommandDefinition definition(
      String commandId,
      String displayName,
      String description,
      BridgeAutomationParameterDefinition... parameters
  ) {
    return new BridgeAutomationCommandDefinition(
        commandId,
        displayName,
        description,
        "cooperative-only",
        false,
        false,
        List.of(parameters)
    );
  }

  private static BridgeAutomationParameterDefinition parameter(
      String name,
      String valueType,
      boolean required,
      String description
  ) {
    return new BridgeAutomationParameterDefinition(name, valueType, required, description);
  }
}
