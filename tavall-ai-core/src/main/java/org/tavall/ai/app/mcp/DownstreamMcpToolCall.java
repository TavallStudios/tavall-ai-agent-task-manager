package org.tavall.ai.app.mcp;

import java.util.Map;

public record DownstreamMcpToolCall(
    String key,
    String serverName,
    String toolName,
    Map<String, Object> arguments
) {

  public DownstreamMcpToolCall {
    arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
  }
}

