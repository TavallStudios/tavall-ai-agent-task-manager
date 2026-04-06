package org.tavall.ai.app.model.orchestration;

import java.util.Locale;

public enum WorkerType {
  CODE(
      "code",
      "Create or change code, produce patches, and carry the implementation path.",
      true,
      true,
      true
  ),
  CLEANUP(
      "cleanup",
      "Review diffs, enforce cleanup standards, and block unsafe changes.",
      false,
      false,
      false
  ),
  COMPUTER_USE(
      "computer-use",
      "Drive reproduction, UI inspection, screenshots, and interactive debugging steps.",
      false,
      false,
      false
  ),
  RETRIEVAL(
      "retrieval",
      "Gather architecture context, prior fixes, and semantic retrieval evidence.",
      false,
      false,
      false
  );

  private final String defaultTaskRole;
  private final String promptFocus;
  private final boolean cleanupReviewRequired;
  private final boolean validationRequired;
  private final boolean integrationTestsSupported;

  WorkerType(
      String defaultTaskRole,
      String promptFocus,
      boolean cleanupReviewRequired,
      boolean validationRequired,
      boolean integrationTestsSupported
  ) {
    this.defaultTaskRole = defaultTaskRole;
    this.promptFocus = promptFocus;
    this.cleanupReviewRequired = cleanupReviewRequired;
    this.validationRequired = validationRequired;
    this.integrationTestsSupported = integrationTestsSupported;
  }

  public String defaultTaskRole() {
    return defaultTaskRole;
  }

  public String promptFocus() {
    return promptFocus;
  }

  public boolean cleanupReviewRequired() {
    return cleanupReviewRequired;
  }

  public boolean validationRequired() {
    return validationRequired;
  }

  public boolean integrationTestsSupported() {
    return integrationTestsSupported;
  }

  public boolean patchArtifactRequired() {
    return this == CODE;
  }

  public static WorkerType fromTaskRole(String taskRole) {
    if (taskRole == null || taskRole.isBlank()) {
      return CODE;
    }
    String normalized = taskRole.strip().toLowerCase(Locale.ROOT);
    if (normalized.contains("cleanup") || normalized.contains("review")) {
      return CLEANUP;
    }
    if (normalized.contains("retrieval")
        || normalized.contains("research")
        || normalized.contains("context")) {
      return RETRIEVAL;
    }
    if (normalized.contains("computer")
        || normalized.contains("debug")
        || normalized.contains("reproduce")
        || normalized.contains("browser")
        || normalized.contains("screen")) {
      return COMPUTER_USE;
    }
    return CODE;
  }

  public static WorkerType fromValue(String value) {
    if (value == null || value.isBlank()) {
      return CODE;
    }
    String normalized = value.strip().replace('-', '_').toUpperCase(Locale.ROOT);
    return WorkerType.valueOf(normalized);
  }
}

