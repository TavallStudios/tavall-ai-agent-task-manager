package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.config.CodexBridgeProperties;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CodexExecCommandFactory {

  private final CodexBridgeProperties properties;
  private final CodexDeterministicConfigService deterministicConfigService;

  public CodexExecCommandFactory(
      CodexBridgeProperties properties,
      CodexDeterministicConfigService deterministicConfigService
  ) {
    this.properties = properties;
    this.deterministicConfigService = deterministicConfigService;
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
    return """
        Execution mode: %s

        Mode policy:
        %s

        Deterministic execution policy:
        Tool access is configured by AgentTaskManager before the run starts.
        Use the configured MCP servers for repository inspection and semantic retrieval first.
        Treat direct shell searching as a fallback only if the required MCP tool cannot satisfy the operation.

        Memory policy:
        Review the memory context before analyzing the user request.
        Keep that memory in working context while planning and while checking the prompt.
        Re-check the memory context before finalizing your answer.
        If the memory conflicts with fresher repository evidence, prefer the repository evidence and mention the conflict.

        Memory context:
        %s

        User request:
        %s
        """.formatted(executionMode, modeInstructions(executionMode), normalizedMemory, promptText.strip());
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
