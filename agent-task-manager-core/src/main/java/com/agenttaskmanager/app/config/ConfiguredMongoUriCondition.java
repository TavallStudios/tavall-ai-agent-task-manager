package com.agenttaskmanager.app.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class ConfiguredMongoUriCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String mongoUri = context.getEnvironment().getProperty("app.mongodb.uri");
    return StringUtils.hasText(mongoUri);
  }
}
