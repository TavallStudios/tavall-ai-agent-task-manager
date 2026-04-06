package org.tavall.ai.app.desktop;

public enum DownstreamMcpMode {
  LOCAL_ONLY("local-only"),
  REMOTE_ONLY("remote-only"),
  LOCAL_THEN_REMOTE("local-then-remote");

  private final String id;

  DownstreamMcpMode(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  public static DownstreamMcpMode from(String value) {
    if (value == null || value.isBlank()) {
      return LOCAL_ONLY;
    }
    String normalized = value.strip().toLowerCase();
    return switch (normalized) {
      case "local", "local-first", "local-only" -> LOCAL_ONLY;
      case "remote", "remote-first", "remote-only" -> REMOTE_ONLY;
      case "both", "try-both", "local-then-remote" -> LOCAL_THEN_REMOTE;
      default -> LOCAL_ONLY;
    };
  }
}
