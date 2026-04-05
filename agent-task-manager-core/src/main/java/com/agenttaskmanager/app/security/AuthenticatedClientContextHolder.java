package com.agenttaskmanager.app.security;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedClientContextHolder {

  private final ThreadLocal<AuthenticatedClientContext> current = new ThreadLocal<>();

  public void set(AuthenticatedClientContext context) {
    current.set(context);
  }

  public Optional<AuthenticatedClientContext> current() {
    return Optional.ofNullable(current.get());
  }

  public void clear() {
    current.remove();
  }
}
