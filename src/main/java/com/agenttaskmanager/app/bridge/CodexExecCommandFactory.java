package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.config.CodexBridgeProperties;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CodexExecCommandFactory {

  private final CodexBridgeProperties properties;

  public CodexExecCommandFactory(CodexBridgeProperties properties) {
    this.properties = properties;
  }

  public List<String> buildCommand(
      Path repoPath,
      String executionMode,
      Path outputFile,
      String resumeSessionId
  ) {
    List<String> command = new ArrayList<>();
    command.add(properties.getCommand());
    command.add("-C");
    command.add(repoPath.toString());
    command.add("-s");
    command.add(resolveSandboxMode(executionMode));
    command.add("exec");
    if (resumeSessionId != null && !resumeSessionId.isBlank()) {
      command.add("resume");
    }
    command.add("--json");
    command.add("--output-last-message");
    command.add(outputFile.toString());
    if (resumeSessionId != null && !resumeSessionId.isBlank()) {
      command.add(resumeSessionId);
    }
    return command;
  }

  public String buildPromptEnvelope(String executionMode, String promptText) {
    return """
        Execution mode: %s

        Mode policy:
        %s

        User request:
        %s
        """.formatted(executionMode, modeInstructions(executionMode), promptText.strip());
  }

  private static String resolveSandboxMode(String executionMode) {
    return "read-only".equals(executionMode) ? "read-only" : "workspace-write";
  }

  private static String modeInstructions(String executionMode) {
    return switch (executionMode) {
      case "read-only" -> "Do not modify files. Investigate, inspect, and report only.";
      case "edit" -> "You may modify files if needed. Implement the requested change and explain the result.";
      case "run-tests" ->
          "You may modify files if needed. Run relevant verification before finishing and report the outcome.";
      default -> "Follow the user request carefully.";
    };
  }
}
