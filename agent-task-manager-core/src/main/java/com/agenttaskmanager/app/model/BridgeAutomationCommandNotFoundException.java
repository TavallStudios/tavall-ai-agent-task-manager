package com.agenttaskmanager.app.model;

public class BridgeAutomationCommandNotFoundException extends RuntimeException {

  public BridgeAutomationCommandNotFoundException(String commandRequestId) {
    super("Bridge automation command not found: " + commandRequestId);
  }
}
