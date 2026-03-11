package com.agenttaskmanager.app.service;

public class PromptRequestNotFoundException extends RuntimeException {

  public PromptRequestNotFoundException(String requestId) {
    super("Prompt request not found: " + requestId);
  }
}

