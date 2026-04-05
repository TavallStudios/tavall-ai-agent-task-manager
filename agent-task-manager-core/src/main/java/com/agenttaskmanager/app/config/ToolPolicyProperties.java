package com.agenttaskmanager.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tool-policy")
public class ToolPolicyProperties {

  private boolean forceHarnessForAllPrompts = true;
  private List<String> forcedToolCalls = new ArrayList<>(List.of("runHarnessToolBundle(repo-context)"));
  private String memoryEnforcementMode = "auto-gate";
  private String gitEnforcementScope = "repo-backed-write";
  private String nativeWindowsShellEnforcementMode = "forbid-powershell";

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

  public String getMemoryEnforcementMode() {
    return memoryEnforcementMode;
  }

  public void setMemoryEnforcementMode(String memoryEnforcementMode) {
    this.memoryEnforcementMode = memoryEnforcementMode;
  }

  public String getGitEnforcementScope() {
    return gitEnforcementScope;
  }

  public void setGitEnforcementScope(String gitEnforcementScope) {
    this.gitEnforcementScope = gitEnforcementScope;
  }

  public String getNativeWindowsShellEnforcementMode() {
    return nativeWindowsShellEnforcementMode;
  }

  public void setNativeWindowsShellEnforcementMode(String nativeWindowsShellEnforcementMode) {
    this.nativeWindowsShellEnforcementMode = nativeWindowsShellEnforcementMode;
  }
}
