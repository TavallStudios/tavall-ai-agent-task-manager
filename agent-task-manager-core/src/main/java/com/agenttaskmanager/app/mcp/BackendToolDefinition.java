package com.agenttaskmanager.app.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BackendToolDefinition(
    String name,
    String displayName,
    String summary,
    String category
) {
}
