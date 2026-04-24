package org.tavall.ai.app.model;

public class PromptRequestNotFoundException extends RuntimeException {

  public PromptRequestNotFoundException(String requestId) {
    super("Prompt request not found: " + requestId);
  }
}


