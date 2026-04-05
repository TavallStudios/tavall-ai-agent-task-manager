package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.config.CodexBridgeProperties;
import com.agenttaskmanager.app.config.ConfiguredCommandResolver;
import com.agenttaskmanager.app.orchestration.ContextualToolPolicyService;
import com.agenttaskmanager.app.orchestration.PromptOutputGuidanceService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CodexExecCommandFactory {

  private final CodexBridgeProperties properties;
  private final ContextualToolPolicyService contextualToolPolicyService;
  private final CodexDeterministicConfigService deterministicConfigService;
  private final PromptOutputGuidanceService promptOutputGuidanceService;

  public CodexExecCommandFactory(
      CodexBridgeProperties properties,
      ContextualToolPolicyService contextualToolPolicyService,
      CodexDeterministicConfigService deterministicConfigService,
      PromptOutputGuidanceService promptOutputGuidanceService
  ) {
    this.properties = properties;
    this.contextualToolPolicyService = contextualToolPolicyService;
    this.deterministicConfigService = deterministicConfigService;
    this.promptOutputGuidanceService = promptOutputGuidanceService;
  }

  public List<String> buildCommand(
      String projectKey,
      Path repoPath,
      String executionMode,
      Path outputFile,
      String resumeSessionId,
      String promptText
  ) {
    List<String> command = new ArrayList<>(ConfiguredCommandResolver.resolveCommand(properties.getCommand()));
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
    if (promptText != null && !promptText.isBlank()) {
      command.add(promptText);
    }
    return command;
  }

  public String buildPromptEnvelope(String executionMode, String promptText) {
    return buildPromptEnvelope(
        executionMode,
        promptText,
        "No related memory context was provided.",
        "No deterministic Java symbol context was preloaded.",
        false
    );
  }

  public String buildPromptEnvelope(String executionMode, String promptText, String memoryContext) {
    return buildPromptEnvelope(
        executionMode,
        promptText,
        memoryContext,
        "No deterministic Java symbol context was preloaded.",
        false
    );
  }

  public String buildPromptEnvelope(
      String executionMode,
      String promptText,
      String memoryContext,
      boolean repoBackedWriteRun
  ) {
    return buildPromptEnvelope(
        executionMode,
        promptText,
        memoryContext,
        "No deterministic Java symbol context was preloaded.",
        repoBackedWriteRun
    );
  }

  public String buildPromptEnvelope(
      String executionMode,
      String promptText,
      String memoryContext,
      String javaSymbolContext,
      boolean repoBackedWriteRun
  ) {
    String normalizedMemory = memoryContext == null || memoryContext.isBlank()
        ? "No related memory context was provided."
        : memoryContext.strip();
    String normalizedJavaSymbolContext = javaSymbolContext == null || javaSymbolContext.isBlank()
        ? "No deterministic Java symbol context was preloaded."
        : javaSymbolContext.strip();
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

        Contextual tool policy:
        %s

        Final response contract:
        %s

        Harness transcript expectation:
        Expect chat-visible harness bootstrap, memory lookup, Java symbol preload, tool policy, observed tool calls, semantic sync, and git workflow status messages when these stages fire.

        Memory context:
        %s

        Java symbol context:
        %s

        User request:
        %s
        """.formatted(
        executionMode,
        modeInstructions(executionMode),
        promptOutputGuidanceService.deterministicExecutionPolicy(),
        promptOutputGuidanceService.memoryPolicy(),
        promptOutputGuidanceService.toolCombinationPatterns(),
        contextualToolPolicyService.buildPolicy(executionMode, normalizedPrompt, false, repoBackedWriteRun),
        promptOutputGuidanceService.finalResponseContract(),
        normalizedMemory,
        normalizedJavaSymbolContext,
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
