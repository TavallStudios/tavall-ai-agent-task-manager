package com.agenttaskmanager.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tool-policy")
public class ToolPolicyProperties {

  private boolean forceHarnessForAllPrompts = true;
  private List<String> forcedToolCalls = new ArrayList<>(List.of("runHarnessToolBundle(repo-context)"));

  public boolean isForceHarnessForAllPrompts() {
    return forceHarnessForAllPrompts;
  }

  public void setForceHarnessForAllPrompts(boolean forceHarnessForAllPrompts) {
    this.forceHarnessForAllPrompts = forceHarnessForAllPrompts;
  }

  public List<String> getForcedToolCalls() {
    return forcedToolCalls;
  }

  public void setForcedToolCalls(List<String> forcedToolCalls) {
    this.forcedToolCalls = forcedToolCalls;
  }
}
