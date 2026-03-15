package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.orchestration.PromptMemoryLookupService;
import org.springframework.stereotype.Service;

@Service
public class BridgePromptMemoryService {

  private final PromptMemoryLookupService promptMemoryLookupService;
  private final CodexExecCommandFactory commandFactory;

  public BridgePromptMemoryService(
      PromptMemoryLookupService promptMemoryLookupService,
      CodexExecCommandFactory commandFactory
  ) {
    this.promptMemoryLookupService = promptMemoryLookupService;
    this.commandFactory = commandFactory;
  }

  public PreparedPrompt preparePrompt(String projectKey, String executionMode, String promptText) {
    PromptMemoryLookupService.PromptMemorySnapshot snapshot = promptMemoryLookupService.lookup(projectKey, promptText);
    return new PreparedPrompt(
        commandFactory.buildPromptEnvelope(executionMode, promptText, snapshot.section()),
        snapshot.summary()
    );
  }

  public record PreparedPrompt(String envelope, String memorySummary) {
  }
}
