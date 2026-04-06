package org.tavall.ai.app.desktop;

import org.tavall.ai.app.model.computeruse.ComputerUseRunnerSummary;
import org.tavall.ai.app.model.computeruse.ComputerUseSessionSummary;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DesktopRemoteRunnerMapper {

  public Map<String, Object> toProfile(ComputerUseRunnerSummary runner, String selectedProfileId) {
    Map<String, Object> metadata = runner.metadata() == null ? Map.of() : runner.metadata();
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("profileId", runner.runnerId());
    profile.put("displayName", runner.displayName());
    profile.put("baseUrl", runner.baseUrl());
    profile.put("transportMode", readString(metadata.get("transportMode"), "DIRECT_HTTP"));
    profile.put("sshHost", readString(metadata.get("sshHost"), ""));
    profile.put("sshPort", readInteger(metadata.get("sshPort"), 22));
    profile.put("sshUser", readString(metadata.get("sshUser"), "ubuntu"));
    profile.put("runnerAuthTokenReference", readString(metadata.get("runnerAuthTokenReference"), ""));
    profile.put("defaultScenarioId", readString(metadata.get("defaultScenarioId"), "hytale/launch-and-join-smoke"));
    profile.put("terminalCommand", readString(metadata.get("terminalCommand"), ""));
    profile.put("selected", runner.runnerId().equalsIgnoreCase(selectedProfileId));
    profile.put("updatedAt", runner.updatedAt() == null ? null : runner.updatedAt().toString());
    return profile;
  }

  public Map<String, Object> toScenario(ComputerUseSessionSummary session) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("sessionId", session.sessionId());
    result.put("runnerId", session.runnerId());
    result.put("scenarioId", session.scenarioId());
    result.put("serverTarget", session.serverTarget());
    result.put("chartId", session.chartId());
    result.put("status", session.status());
    result.put("latestSummary", session.latestSummary());
    result.put("createdAt", session.createdAt() == null ? null : session.createdAt().toString());
    result.put("updatedAt", session.updatedAt() == null ? null : session.updatedAt().toString());
    result.put("completedAt", session.completedAt() == null ? null : session.completedAt().toString());
    return result;
  }

  public String readCommandPath(Map<String, Object> capabilityBody, String fallback) {
    if (!(capabilityBody.get("result") instanceof Map<?, ?> result)) {
      return fallback;
    }
    if (!(result.get("endpoints") instanceof Map<?, ?> endpoints)) {
      return fallback;
    }
    Object command = endpoints.get("command");
    String commandPath = command == null ? "" : String.valueOf(command).strip();
    return commandPath.isBlank() ? fallback : commandPath;
  }

  public String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "";
    }
    return baseUrl.strip().replaceAll("/+$", "");
  }

  public String extractHost(String baseUrl) {
    try {
      URI uri = URI.create(baseUrl);
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        return "runner-host";
      }
      return uri.getHost();
    } catch (Exception exception) {
      return "runner-host";
    }
  }

  public String readString(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String text = String.valueOf(value).strip();
    return text.isBlank() ? fallback : text;
  }

  public int readInteger(Object value, int fallback) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text) {
      try {
        return Integer.parseInt(text.strip());
      } catch (NumberFormatException ignored) {
      }
    }
    return fallback;
  }

  public boolean readBoolean(Object value, boolean fallback) {
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    if (value instanceof String text) {
      if ("true".equalsIgnoreCase(text)) {
        return true;
      }
      if ("false".equalsIgnoreCase(text)) {
        return false;
      }
    }
    return fallback;
  }
}


