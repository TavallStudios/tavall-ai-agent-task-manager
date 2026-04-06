package org.tavall.ai.app.security;

import java.util.List;

public record AuthenticatedClientContext(
    String authenticationMode,
    String principalName,
    String apiKeyId,
    String workspaceId,
    String userId,
    String defaultProjectId,
    List<String> roles
) {

  public String requestedBy() {
    return principalName == null || principalName.isBlank() ? "mcp-client" : principalName.strip();
  }
}

