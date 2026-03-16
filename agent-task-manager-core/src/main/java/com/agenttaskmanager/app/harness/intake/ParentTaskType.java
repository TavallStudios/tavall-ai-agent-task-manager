package com.agenttaskmanager.app.harness.intake;

import java.util.Locale;

public enum ParentTaskType {
  FIX_OUTPUT,
  REFACTOR_FEATURE,
  DEBUG_ISSUE,
  VALIDATE_PATCH,
  REPRODUCE_BUG,
  CLEANUP_DIFFS,
  GENERAL;

  public static ParentTaskType fromValue(String value) {
    if (value == null || value.isBlank()) {
      return GENERAL;
    }
    return ParentTaskType.valueOf(value.strip().replace('-', '_').toUpperCase(Locale.ROOT));
  }
}
