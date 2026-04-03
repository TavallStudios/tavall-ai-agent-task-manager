package com.agenttaskmanager.app.service.session;

import com.agenttaskmanager.app.model.session.CodexSessionApiModels.RuntimeConnectionState;
import com.agenttaskmanager.app.model.session.CodexSessionEventModels.SessionEventEnvelope;
import com.agenttaskmanager.app.model.session.CodexSessionEventModels.SessionEventType;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class InMemorySessionServiceSupport {

  private InMemorySessionServiceSupport() {
  }

  static InMemoryCodexSessionState requireSession(
      Map<String, InMemoryCodexSessionState> sessions,
      String sessionId
  ) {
    InMemoryCodexSessionState session = sessions.get(sessionId);
    if (session == null) {
      throw new CodexSessionNotFoundException(sessionId);
    }
    return session;
  }

  static int afterEventIdIndex(List<SessionEventEnvelope> events, String afterEventId) {
    if (afterEventId == null || afterEventId.isBlank()) {
      return 0;
    }
    for (int index = 0; index < events.size(); index++) {
      if (afterEventId.equals(events.get(index).eventId())) {
        return index + 1;
      }
    }
    return 0;
  }

  static void publish(
      InMemoryCodexSessionState session,
      InMemorySessionEventBroker eventBroker,
      String eventSchemaVersion,
      String turnId,
      SessionEventType eventType,
      String source,
      OffsetDateTime occurredAt,
      Map<String, Object> attributes,
      String summary
  ) {
    SessionEventEnvelope event = new SessionEventEnvelope(
        "evt_" + UUID.randomUUID(),
        session.sessionId,
        turnId,
        eventType,
        eventSchemaVersion,
        source,
        occurredAt,
        new LinkedHashMap<>(attributes),
        summary
    );
    session.events.add(event);
    session.lastEventAt = occurredAt;
    eventBroker.publish(event);
  }

  static RuntimeConnectionState parseConnectionState(String value) {
    if (value == null || value.isBlank()) {
      return RuntimeConnectionState.DISCONNECTED;
    }
    return RuntimeConnectionState.valueOf(value.trim().toUpperCase());
  }

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
