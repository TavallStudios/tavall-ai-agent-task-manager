package com.agenttaskmanager.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

  private String username = "agent";
  private String password = "";
  private String rememberMeKey = "agent-task-manager-remember-me";
  private boolean mcpNoAuthEnabled = false;
  private boolean proxyAuthEnabled = false;
  private String proxyAuthHeader = "X-Forwarded-User";

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

  public boolean isMcpNoAuthEnabled() {
    return mcpNoAuthEnabled;
  }

  public void setMcpNoAuthEnabled(boolean mcpNoAuthEnabled) {
    this.mcpNoAuthEnabled = mcpNoAuthEnabled;
  }

  public boolean isProxyAuthEnabled() {
    return proxyAuthEnabled;
  }

  public void setProxyAuthEnabled(boolean proxyAuthEnabled) {
    this.proxyAuthEnabled = proxyAuthEnabled;
  }

  public String getProxyAuthHeader() {
    return proxyAuthHeader;
  }

  public void setProxyAuthHeader(String proxyAuthHeader) {
    this.proxyAuthHeader = proxyAuthHeader;
  }
}

