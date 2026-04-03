package com.agenttaskmanager.app.service.session;

import com.agenttaskmanager.app.model.session.CodexSessionEventModels.SessionEventEnvelope;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.codex-client-platform", name = "enabled", havingValue = "true")
public class InMemorySessionEventBroker {

  private final Map<String, CopyOnWriteArrayList<Consumer<SessionEventEnvelope>>> subscribers =
      new ConcurrentHashMap<>();

  public SessionEventSubscription subscribe(
      String sessionId,
      List<SessionEventEnvelope> replayEvents,
      Consumer<SessionEventEnvelope> consumer
  ) {
    subscribers.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArrayList<>()).add(consumer);
    replayEvents.forEach(consumer);
    return () -> unsubscribe(sessionId, consumer);
  }

  public void publish(SessionEventEnvelope event) {
    CopyOnWriteArrayList<Consumer<SessionEventEnvelope>> scopedSubscribers = subscribers.get(event.sessionId());
    if (scopedSubscribers == null) {
      return;
    }

    scopedSubscribers.forEach(listener -> publishToListener(event, scopedSubscribers, listener));
  }

  private void unsubscribe(String sessionId, Consumer<SessionEventEnvelope> consumer) {
    CopyOnWriteArrayList<Consumer<SessionEventEnvelope>> scopedSubscribers = subscribers.get(sessionId);
    if (scopedSubscribers == null) {
      return;
    }
    scopedSubscribers.remove(consumer);
    if (scopedSubscribers.isEmpty()) {
      subscribers.remove(sessionId, scopedSubscribers);
    }
  }

  private static void publishToListener(
      SessionEventEnvelope event,
      CopyOnWriteArrayList<Consumer<SessionEventEnvelope>> scopedSubscribers,
      Consumer<SessionEventEnvelope> listener
  ) {
    try {
      listener.accept(event);
    } catch (RuntimeException exception) {
      scopedSubscribers.remove(listener);
    }
  }
}
