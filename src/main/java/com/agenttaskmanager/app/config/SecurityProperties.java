package com.agenttaskmanager.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

  private String username = "agent";
  private String password = "";
  private String rememberMeKey = "agent-task-manager-remember-me";

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getRememberMeKey() {
    return rememberMeKey;
  }

  public void setRememberMeKey(String rememberMeKey) {
    this.rememberMeKey = rememberMeKey;
  }
}

