package org.tavall.ai.app.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class MissingDataSourceUrlCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String jdbcUrl = context.getEnvironment().getProperty("spring.datasource.url");
    return !StringUtils.hasText(jdbcUrl);
  }
}

