package org.tavall.ai.app.harness.tools;

import java.util.Arrays;

public enum HarnessToolBundleType {
  REPO_CONTEXT("repo-context"),
  WORKER_CONTEXT("worker-context"),
  LANGUAGE_CONTEXT("language-context");

  private final String value;

  HarnessToolBundleType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static HarnessToolBundleType fromValue(String value) {
    if (value == null || value.isBlank()) {
      return WORKER_CONTEXT;
    }
    String normalized = value.strip().replace('_', '-').toLowerCase();
    if ("java-context".equals(normalized)) {
      return LANGUAGE_CONTEXT;
    }
    return Arrays.stream(values())
        .filter(candidate -> candidate.value.equals(normalized) || candidate.name().toLowerCase().replace('_', '-').equals(normalized))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown harness tool bundle: " + value));
  }
}

