package com.agenttaskmanager.app.model;

public class PromptRequestNotFoundException extends RuntimeException {

  public PromptRequestNotFoundException(String requestId) {
    super("Prompt request not found: " + requestId);
  }
}

