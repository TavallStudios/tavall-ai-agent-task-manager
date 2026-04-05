package com.agenttaskmanager.app.security;

import java.util.List;
import java.util.Map;

public record McpApiKeyRecord(
    String apiKeyId,
    String displayName,
    String keyHash,
    String userId,
    String workspaceId,
    String projectId,
    String status,
    List<String> roles,
    Map<String, Object> metadata
) {
}
