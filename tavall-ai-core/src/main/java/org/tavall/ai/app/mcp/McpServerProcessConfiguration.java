package org.tavall.ai.app.mcp;

import java.util.List;
import java.util.Map;

public record McpServerProcessConfiguration(
    String serverName,
    String command,
    List<String> args,
    Map<String, String> env
) {
}

