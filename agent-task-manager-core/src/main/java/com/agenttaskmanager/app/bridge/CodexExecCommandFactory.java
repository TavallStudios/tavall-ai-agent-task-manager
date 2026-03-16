package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.config.CodexBridgeProperties;
import com.agenttaskmanager.app.orchestration.PromptOutputGuidanceService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CodexExecCommandFactory {

  private final CodexBridgeProperties properties;
  private final CodexDeterministicConfigService deterministicConfigService;
  private final PromptOutputGuidanceService promptOutputGuidanceService;

  public CodexExecCommandFactory(
      CodexBridgeProperties properties,
      CodexDeterministicConfigService deterministicConfigService,
      PromptOutputGuidanceService promptOutputGuidanceService
  ) {
    this.properties = properties;
    this.deterministicConfigService = deterministicConfigService;
    this.promptOutputGuidanceService = promptOutputGuidanceService;
  }

  public List<String> buildCommand(
      String projectKey,
      Path repoPath,
      String executionMode,
      Path outputFile,
      String resumeSessionId
  ) {
    List<String> command = new ArrayList<>();
    command.add(properties.getCommand());
    deterministicConfigService.appendDeterministicArguments(command, projectKey);
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
    return buildPromptEnvelope(executionMode, promptText, "No related memory context was provided.");
  }

  public String buildPromptEnvelope(String executionMode, String promptText, String memoryContext) {
    String normalizedMemory = memoryContext == null || memoryContext.isBlank()
        ? "No related memory context was provided."
        : memoryContext.strip();
    String normalizedPrompt = promptText == null || promptText.isBlank()
        ? "No user request was provided."
        : promptText.strip();
    return """
        Execution mode: %s

        Mode policy:
        %s

        Deterministic execution policy:
        %s

        Memory policy:
        %s

        Tool combination patterns:
        %s

        Final response contract:
        %s

        Memory context:
        %s

        User request:
        %s
        """.formatted(
        executionMode,
        modeInstructions(executionMode),
        promptOutputGuidanceService.deterministicExecutionPolicy(),
        promptOutputGuidanceService.memoryPolicy(),
        promptOutputGuidanceService.toolCombinationPatterns(),
        promptOutputGuidanceService.finalResponseContract(),
        normalizedMemory,
        normalizedPrompt
    );
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
