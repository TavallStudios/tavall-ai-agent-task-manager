package com.agenttaskmanager.app.mcp;

public record DownstreamMcpToolResult(
    String key,
    String serverName,
    String toolName,
    String status,
    Object structuredContent,
    String textContent,
    String stderr,
    String errorMessage,
    long durationMs
) {

  public boolean isError() {
    return !"completed".equals(status);
  }
}
