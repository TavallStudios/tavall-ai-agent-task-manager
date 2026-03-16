package com.agenttaskmanager.app.cli;

import java.util.List;
import java.util.Map;

public record RemoteMcpSmokeResult(
    String baseUrl,
    String endpoint,
    String protocolVersion,
    String serverName,
    String serverVersion,
    List<String> toolNames,
    List<String> resourceNames,
    List<String> promptNames,
    Map<String, Object> dashboardSummary
) {
}
