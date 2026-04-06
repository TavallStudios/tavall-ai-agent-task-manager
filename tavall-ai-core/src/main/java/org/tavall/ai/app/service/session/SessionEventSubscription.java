package org.tavall.ai.app.service.session;

@FunctionalInterface
public interface SessionEventSubscription extends AutoCloseable {

  @Override
  void close();
}

