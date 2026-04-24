package org.tavall.ai.app.bridge;

public record CodexEventMessage(String kind, String sender, String body, String toolName) {

  public CodexEventMessage(String kind, String sender, String body) {
    this(kind, sender, body, "");
  }
}

