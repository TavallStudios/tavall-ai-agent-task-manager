package com.agenttaskmanager.app.service.session;

@FunctionalInterface
public interface SessionEventSubscription extends AutoCloseable {

  @Override
  void close();
}
