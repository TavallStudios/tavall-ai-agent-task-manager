package org.tavall.ai.app.desktop;

public enum DesktopMcpServerMode {
  LOCAL_ONLY("local-only"),
  REMOTE_ONLY("remote-only"),
  LOCAL_THEN_REMOTE("local-then-remote"),
  REMOTE_THEN_LOCAL("remote-then-local");

  private final String id;

  DesktopMcpServerMode(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  public static DesktopMcpServerMode from(String value) {
    if (value == null || value.isBlank()) {
      return LOCAL_ONLY;
    }
    String normalized = value.strip().toLowerCase();
    return switch (normalized) {
      case "local", "local-only", "local-first" -> LOCAL_ONLY;
      case "remote", "remote-only", "remote-first" -> REMOTE_ONLY;
      case "local-then-remote", "local-remote", "try-local-remote" -> LOCAL_THEN_REMOTE;
      case "remote-then-local", "remote-local", "try-remote-local" -> REMOTE_THEN_LOCAL;
      default -> LOCAL_ONLY;
    };
  }
}
