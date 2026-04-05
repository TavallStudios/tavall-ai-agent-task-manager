package com.agenttaskmanager.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

  private String username = "agent";
  private String password = "";
  private String rememberMeKey = "agent-task-manager-remember-me";
  private boolean mcpNoAuthEnabled = false;
  private boolean proxyAuthEnabled = false;
  private String proxyAuthHeader = "X-Forwarded-User";
  private String apiKeyHeader = "X-Agent-Api-Key";
  private List<ApiKeySeed> bootstrapApiKeys = new ArrayList<>();

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

  public String getApiKeyHeader() {
    return apiKeyHeader;
  }

  public void setApiKeyHeader(String apiKeyHeader) {
    this.apiKeyHeader = apiKeyHeader;
  }

  public List<ApiKeySeed> getBootstrapApiKeys() {
    return bootstrapApiKeys;
  }

  public void setBootstrapApiKeys(List<ApiKeySeed> bootstrapApiKeys) {
    this.bootstrapApiKeys = bootstrapApiKeys == null ? new ArrayList<>() : bootstrapApiKeys;
  }

  public static class ApiKeySeed {

    private String apiKeyId = "";
    private String token = "";
    private String displayName = "";
    private String workspaceId = "";
    private String userId = "";
    private String projectId = "";
    private List<String> roles = List.of();

    public String getApiKeyId() {
      return apiKeyId;
    }

    public void setApiKeyId(String apiKeyId) {
      this.apiKeyId = apiKeyId == null ? "" : apiKeyId;
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token == null ? "" : token;
    }

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName == null ? "" : displayName;
    }

    public String getWorkspaceId() {
      return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
      this.workspaceId = workspaceId == null ? "" : workspaceId;
    }

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId == null ? "" : userId;
    }

    public String getProjectId() {
      return projectId;
    }

    public void setProjectId(String projectId) {
      this.projectId = projectId == null ? "" : projectId;
    }

    public List<String> getRoles() {
      return roles;
    }

    public void setRoles(List<String> roles) {
      this.roles = roles == null ? List.of() : List.copyOf(roles);
    }
  }
}

