package org.tavall.ai.app.service.session;

public class CodexSessionNotFoundException extends RuntimeException {

  public CodexSessionNotFoundException(String sessionId) {
    super("Codex session not found: " + sessionId);
  }
}

