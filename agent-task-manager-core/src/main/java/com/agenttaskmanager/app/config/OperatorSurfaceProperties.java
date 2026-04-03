package com.agenttaskmanager.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.operator")
public class OperatorSurfaceProperties {

  private String externalBaseUrl = "https://docs.tavall.org";
  private String dashboardPath = "/agent-task-manager/";
  private String idePath = "/code/";
  private String ideHealthUrl = "http://127.0.0.1:13337/healthz";
  private String ideWorkspace = "/srv";
  private String supportCommand = "bash -lc";

  public String getExternalBaseUrl() {
    return externalBaseUrl;
  }

  public void setExternalBaseUrl(String externalBaseUrl) {
    this.externalBaseUrl = externalBaseUrl;
  }

  public String getDashboardPath() {
    return dashboardPath;
  }

  public void setDashboardPath(String dashboardPath) {
    this.dashboardPath = dashboardPath;
  }

  public String getIdePath() {
    return idePath;
  }

  public void setIdePath(String idePath) {
    this.idePath = idePath;
  }

  public String getIdeHealthUrl() {
    return ideHealthUrl;
  }

  public void setIdeHealthUrl(String ideHealthUrl) {
    this.ideHealthUrl = ideHealthUrl;
  }

  public String getIdeWorkspace() {
    return ideWorkspace;
  }

  public void setIdeWorkspace(String ideWorkspace) {
    this.ideWorkspace = ideWorkspace;
  }

  public String getSupportCommand() {
    return supportCommand;
  }

  public void setSupportCommand(String supportCommand) {
    this.supportCommand = supportCommand;
  }
}
