package org.tavall.ai.app.bridge;

import org.tavall.ai.app.config.CodexBridgeProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RemoteHeadlessPromptLoopService {

  private final CodexBridgeProperties bridgeProperties;
  private final RemoteHeadlessPromptExecutionService executionService;

  public RemoteHeadlessPromptLoopService(
      CodexBridgeProperties bridgeProperties,
      RemoteHeadlessPromptExecutionService executionService
  ) {
    this.bridgeProperties = bridgeProperties;
    this.executionService = executionService;
  }

  @Scheduled(
      initialDelayString = "${app.bridge.poll-interval-ms:5000}",
      fixedDelayString = "${app.bridge.poll-interval-ms:5000}"
  )
  public void runLoop() {
    if (!bridgeProperties.isEnabled()) {
      return;
    }
    executionService.executeNextQueued();
  }
}

