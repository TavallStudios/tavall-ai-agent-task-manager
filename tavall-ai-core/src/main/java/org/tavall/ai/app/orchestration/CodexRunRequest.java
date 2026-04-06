package org.tavall.ai.app.orchestration;

import org.tavall.ai.app.bridge.CodexEventMessage;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public record CodexRunRequest(
    List<String> command,
    Path workspacePath,
    Path outputFile,
    String baseRevision,
    String finalResponseFallback,
    ContextualToolPolicyService.ToolPolicyDecision toolPolicyDecision,
    ContextualToolPolicyService.HarnessMemoryEvidence harnessMemoryEvidence,
    Consumer<CodexEventMessage> eventConsumer,
    CodexRuntimePlatform runtimePlatformOverride
) {

  public CodexRunRequest(
      List<String> command,
      Path workspacePath,
      Path outputFile,
      String baseRevision,
      String finalResponseFallback,
      ContextualToolPolicyService.ToolPolicyDecision toolPolicyDecision,
      ContextualToolPolicyService.HarnessMemoryEvidence harnessMemoryEvidence,
      Consumer<CodexEventMessage> eventConsumer
  ) {
    this(
        command,
        workspacePath,
        outputFile,
        baseRevision,
        finalResponseFallback,
        toolPolicyDecision,
        harnessMemoryEvidence,
        eventConsumer,
        null
    );
  }
}

