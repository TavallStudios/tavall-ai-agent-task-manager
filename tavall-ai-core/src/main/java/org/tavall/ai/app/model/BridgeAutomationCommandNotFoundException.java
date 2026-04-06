package org.tavall.ai.app.model;

public class BridgeAutomationCommandNotFoundException extends RuntimeException {

  public BridgeAutomationCommandNotFoundException(String commandRequestId) {
    super("Bridge automation command not found: " + commandRequestId);
  }
}

