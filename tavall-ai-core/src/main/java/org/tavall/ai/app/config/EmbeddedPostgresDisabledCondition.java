package org.tavall.ai.app.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class EmbeddedPostgresDisabledCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return !context.getEnvironment()
        .getProperty("tavall.ai.embedded-postgres.enabled", Boolean.class, true);
  }
}
