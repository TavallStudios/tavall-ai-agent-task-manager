package org.tavall.ai.app.orchestration;

import org.tavall.ai.app.config.ToolPolicyProperties;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class NativeWindowsShellPolicySupport {

  private final ToolPolicyProperties properties;

  public NativeWindowsShellPolicySupport(ToolPolicyProperties properties) {
    this.properties = properties;
  }

  public String enforcementMode() {
    return normalizeMode(properties.getNativeWindowsShellEnforcementMode());
  }

  public Set<String> validate(
      CodexRuntimePlatform runtimePlatform,
      Set<CodexToolCallObservation> observations,
      Set<String> violations
  ) {
    if (runtimePlatform != CodexRuntimePlatform.WINDOWS_NATIVE) {
      return Set.of();
    }
    if (!"forbid-powershell".equals(enforcementMode())) {
      return Set.of();
    }
    Set<String> forbiddenToolCalls = new LinkedHashSet<>();
    for (CodexToolCallObservation observation : observations) {
      if ("shellcommand".equals(normalize(observation.toolName()))) {
        forbiddenToolCalls.add(displayName(observation));
      }
    }
    if (!forbiddenToolCalls.isEmpty()) {
      violations.add(
          "Native Windows Codex runs must not use shell_command because it executes through PowerShell. "
              + "Use runHarnessToolBundle(repo-context) and first-party MCP tools instead."
      );
    }
    return forbiddenToolCalls;
  }

  private String displayName(CodexToolCallObservation observation) {
    String toolName = observation.toolName();
    if (toolName != null && !toolName.isBlank()) {
      return toolName.strip();
    }
    return observation.signature() == null ? "" : observation.signature().strip();
  }

  private String normalizeMode(String value) {
    String normalized = normalize(value);
    if (normalized.isBlank()) {
      return "forbid-powershell";
    }
    return switch (normalized) {
      case "disabled", "off", "none" -> "disabled";
      case "forbidpowershell", "powershellonly", "enabled" -> "forbid-powershell";
      default -> "forbid-powershell";
    };
  }

  private String normalize(String value) {
    return value == null
        ? ""
        : value.toLowerCase(Locale.ROOT).strip().replace(" ", "").replace("_", "").replace("-", "");
  }
}

