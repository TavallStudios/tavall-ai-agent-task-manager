package com.agenttaskmanager.app.model;

public class BridgeSessionNotFoundException extends RuntimeException {

  public BridgeSessionNotFoundException(String sessionId) {
    super("Bridge session not found: " + sessionId);
  }
}
