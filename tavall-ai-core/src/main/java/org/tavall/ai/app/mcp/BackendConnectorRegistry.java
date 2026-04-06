package org.tavall.ai.app.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BackendConnectorRegistry(
    int version,
    String centralServer,
    List<BackendConnectorDefinition> connectors
) {

  public BackendConnectorRegistry {
    connectors = connectors == null ? List.of() : List.copyOf(connectors);
  }
}

