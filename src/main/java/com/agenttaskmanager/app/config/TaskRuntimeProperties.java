package com.agenttaskmanager.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.runtime")
public class TaskRuntimeProperties {

  private String redisNamespace = "agent-task-manager:tasks";

  public String getRedisNamespace() {
    return redisNamespace;
  }

  public void setRedisNamespace(String redisNamespace) {
    this.redisNamespace = redisNamespace;
  }
}

