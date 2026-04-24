package org.tavall.ai.app.hytalelearning;

import org.tavall.ai.app.model.hytalelearning.HytaleLearningSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HytaleLearningProjectKeyFactory {

  public String projectKey(
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId
  ) {
    return String.join(
        ":",
        "hytale",
        slug(machineId),
        slug(clientProfileId),
        slug(serverTarget),
        slug(scenarioId)
    );
  }

  public String projectKey(HytaleLearningSession session) {
    return projectKey(
        session.machineId(),
        session.clientProfileId(),
        session.serverTarget(),
        session.scenarioId()
    );
  }

  public Map<String, Object> semanticScope(
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId
  ) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("machineId", machineId == null ? "" : machineId);
    payload.put("clientProfileId", clientProfileId == null ? "" : clientProfileId);
    payload.put("serverTarget", serverTarget == null ? "" : serverTarget);
    payload.put("scenarioId", scenarioId == null ? "" : scenarioId);
    payload.put("semanticDomain", "hytale");
    return payload;
  }

  private String slug(String value) {
    if (value == null || value.isBlank()) {
      return "default";
    }
    return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }
}

