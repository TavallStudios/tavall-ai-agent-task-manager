package com.agenttaskmanager.app.orchestration;

public enum CodexRuntimePlatform {
  WINDOWS_NATIVE("windows-native"),
  WINDOWS_WSL("windows-wsl"),
  NON_WINDOWS("non-windows");

  private final String value;

  CodexRuntimePlatform(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
