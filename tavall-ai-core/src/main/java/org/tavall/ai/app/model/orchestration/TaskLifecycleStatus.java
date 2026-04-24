package org.tavall.ai.app.model.orchestration;

public enum TaskLifecycleStatus {
  CREATED,
  QUEUED,
  ASSIGNED,
  RUNNING,
  CHECKED_IN,
  BLOCKED,
  FAILED,
  COMPLETED,
  DEAD,
  DEAD_LETTER,
  REASSIGNED,
  UNDER_REVIEW,
  NEEDS_REWORK,
  APPROVED
}

