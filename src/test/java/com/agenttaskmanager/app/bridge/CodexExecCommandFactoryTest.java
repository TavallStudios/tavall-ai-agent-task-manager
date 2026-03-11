package com.agenttaskmanager.app.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.config.CodexBridgeProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodexExecCommandFactoryTest {

  @Test
  void shouldBuildReadOnlyCommand() {
    CodexBridgeProperties properties = new CodexBridgeProperties();
    properties.setCommand("codex");
    CodexExecCommandFactory factory = new CodexExecCommandFactory(properties);

    List<String> command = factory.buildCommand(
        Path.of("/srv/AgentTaskManager"),
        "read-only",
        Path.of("/tmp/out.txt")
    );

    assertEquals("codex", command.getFirst());
    assertTrue(command.contains("exec"));
    assertTrue(command.contains("read-only"));
  }

  @Test
  void shouldAddModeInstructionsToPromptEnvelope() {
    CodexBridgeProperties properties = new CodexBridgeProperties();
    CodexExecCommandFactory factory = new CodexExecCommandFactory(properties);

    String prompt = factory.buildPromptEnvelope("run-tests", "Fix the failing build.");

    assertTrue(prompt.contains("Execution mode: run-tests"));
    assertTrue(prompt.contains("Run relevant verification"));
    assertTrue(prompt.contains("Fix the failing build."));
  }
}

