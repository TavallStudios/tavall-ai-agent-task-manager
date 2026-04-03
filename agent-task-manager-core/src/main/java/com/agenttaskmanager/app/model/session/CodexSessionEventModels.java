package com.agenttaskmanager.app.model.session;

import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import java.util.Map;

public final class CodexSessionEventModels {

  private CodexSessionEventModels() {
  }

  public enum SessionEventType {
    SESSION_CREATED("SessionCreated"),
    SESSION_ATTACHED("SessionAttached"),
    SESSION_RESUMED("SessionResumed"),
    WORKSPACE_BOUND("WorkspaceBound"),
    THREAD_STARTED("ThreadStarted"),
    TURN_STARTED("TurnStarted"),
    TURN_DELTA_RECEIVED("TurnDeltaReceived"),
    TOOL_CALL_REQUESTED("ToolCallRequested"),
    TOOL_RECEIPT_PUBLISHED("ToolReceiptPublished"),
    VERIFIER_STARTED("VerifierStarted"),
    VERIFIER_FAILED("VerifierFailed"),
    VERIFIER_PASSED("VerifierPassed"),
    CANDIDATE_OUTPUT_PRODUCED("CandidateOutputProduced"),
    APPROVED_OUTPUT_RELEASED("ApprovedOutputReleased"),
    OUTPUT_BLOCKED("OutputBlocked"),
    PATCH_PUBLISHED("PatchPublished"),
    FILE_FOCUS_REQUESTED("FileFocusRequested"),
    EXTERNAL_EDITOR_OPEN_REQUESTED("ExternalEditorOpenRequested"),
    RUNTIME_DISCONNECTED("RuntimeDisconnected"),
    RUNTIME_RECONNECTED("RuntimeReconnected"),
    SESSION_COMPLETED("SessionCompleted");

    private final String wireName;

    SessionEventType(String wireName) {
      this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
      return wireName;
    }

    public static SessionEventType fromWireName(String value) {
      for (SessionEventType candidate : values()) {
        if (candidate.wireName.equalsIgnoreCase(value)) {
          return candidate;
        }
      }
      throw new IllegalArgumentException("Unknown session event type: " + value);
    }
  }

  public record SessionEventEnvelope(
      String eventId,
      String sessionId,
      String turnId,
      SessionEventType eventType,
      String schemaVersion,
      String source,
      OffsetDateTime occurredAt,
      Map<String, Object> attributes,
      String summary
  ) {
  }
}
